package family.fisa.hangangpay.domain.transaction.service.payment.v0;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.PaymentResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.bank.BankOutcome;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentIntentResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentCommandService;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.domain.transaction.service.support.BankCallExecutor;
import family.fisa.hangangpay.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAYMENT(결제) 명령 오케스트레이터.
 *
 * <p>동시 실행 직렬화는 StateWriter의 payer wallet 비관적 락(NOWAIT), 멱등성은 멱등 스토어가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class PaymentCommandServiceV0 implements PaymentCommandService {

    private static final long PAYMENT_INTENT_TTL_MINUTES = 10L;

    /** 결제 종단 실패로 매핑할 bank 오류 코드. 미매핑 코드는 fallback(PAYMENT_FAILED). */
    private static final Map<String, BaseErrorCode> BANK_FAIL_CODE_MAP =
            Map.of(
                    "TRANSACTION_INSUFFICIENT_BALANCE",
                            TransactionErrorCode.PAYMENT_INSUFFICIENT_BALANCE,
                    "TRANSACTION_ALREADY_FAILED", TransactionErrorCode.PAYMENT_ALREADY_FAILED);

    private final TransactionRepository transactionRepository;
    private final MerchantRepository merchantRepository;
    private final WalletRepository walletRepository;
    private final PartyRepository partyRepository;
    private final BankClient bankClient;
    private final PaymentStateWriter paymentStateWriter;
    private final PaymentIdempotencyStore paymentIdempotencyStore;
    private final BankCallExecutor bankCallExecutor;

    /** 결제 intent 생성 - 서버 발급 transactionUuid로 PENDING 결제 의도를 만든다. */
    @Override
    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            Long partyId, PaymentIntentCreateRequest request) {

        // 1. 결제자·가맹점·양쪽 지갑을 조회한다.
        Party userParty = getParty(partyId);
        Merchant merchant = getMerchant(request.merchantPartyId());
        Wallet userWallet = getWallet(partyId);
        Wallet merchantWallet = getWallet(request.merchantPartyId());

        // 2. 10분 이내 같은 (결제자 -> 가맹점 -> 금액)의 살아있는 PENDING이 있으면 그대로 재사용한다.
        LocalDateTime liveThreshold = LocalDateTime.now().minusMinutes(PAYMENT_INTENT_TTL_MINUTES);
        Optional<Transaction> live =
                transactionRepository.findLivePendingPayment(
                        userParty.getId(),
                        merchant.getParty().getId(),
                        request.amount(),
                        liveThreshold);
        if (live.isPresent()) {
            return PaymentIntentResponse.from(live.get(), merchant, intentExpiresAt(live.get()));
        }

        // 3. 없으면 서버가 uuid를 발급해 새 PENDING 결제 의도를 저장한다.
        String transactionUuid = UUID.randomUUID().toString();
        Transaction transaction =
                Transaction.forPayment(
                        transactionUuid,
                        userParty,
                        merchant.getParty(),
                        userWallet,
                        merchantWallet,
                        request.amount(),
                        null,
                        request.itemName());

        Transaction saved = transactionRepository.save(transaction);

        // 4. 만료 시각과 함께 응답을 만든다.
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(PAYMENT_INTENT_TTL_MINUTES);
        return PaymentIntentResponse.from(saved, merchant, expiresAt);
    }

    /** 결제 실행 - 멱등성 판단 -> 은행 결제 요청 -> 상태 전환. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecuteResponse executePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request) {

        // 1. 락 + 멱등 판단 + PROCESSING 전환 (StateWriter의 별도 트랜잭션). 재요청이면 여기서 snapshot이 온다.
        PaymentExecutionPreparationResult result =
                paymentStateWriter.prepareExecution(
                        userId, partyId, transactionUuid, request.paymentPin());

        // 2. 이미 완료된 요청이면 저장된 응답을 그대로 반환한다.
        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        PaymentExecutionPrepared prepared = result.prepared();

        // 3. 은행에 결제를 요청한다 (락 밖에서 호출, 일시적 오류는 1회 재시도).
        BankOutcome<PaymentResponse> outcome =
                bankCallExecutor.callBankWithRetry(
                        () -> bankClient.payment(prepared.toBankPaymentRequest()),
                        BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.PAYMENT_FAILED);

        // 4. 은행 결과에 따라 거래 상태를 확정한다 (성공/불명/종단실패).
        PaymentExecuteResponse response =
                switch (outcome.type()) {
                    case SUCCESS ->
                            paymentStateWriter.completeSuccess(
                                    transactionUuid, null, null, outcome.value().confirmedAt());
                    case UNKNOWN -> paymentStateWriter.markUnknown(transactionUuid);
                    case TERMINAL_FAILED -> {
                        paymentStateWriter.completeFailed(transactionUuid);
                        throw new BusinessException(outcome.errorCode());
                    }
                };

        // 5. 완료 응답을 멱등 스토어에 저장한다 (재요청 시 재사용).
        paymentIdempotencyStore.completeExecution(transactionUuid, response);
        return response;
    }

    private Party getParty(Long partyId) {
        return partyRepository
                .findById(partyId)
                .orElseThrow(() -> new BusinessException(GeneralErrorCode.COMMON_NOT_FOUND));
    }

    private Merchant getMerchant(Long merchantPartyId) {
        return merchantRepository
                .findByParty_Id(merchantPartyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private Wallet getWallet(Long partyId) {
        return walletRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
    }

    private LocalDateTime intentExpiresAt(Transaction transaction) {
        return transaction.getCreatedAt().plusMinutes(PAYMENT_INTENT_TTL_MINUTES);
    }
}
