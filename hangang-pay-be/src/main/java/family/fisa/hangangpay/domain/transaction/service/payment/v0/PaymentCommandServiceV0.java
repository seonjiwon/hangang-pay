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
 * <p>동시성 직렬화와 멱등성은 payer wallet 비관적 락 + 거래 상태로 처리한다.
 */
// @Service
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
    private final BankCallExecutor bankCallExecutor;

    /** 결제 intent 생성 - 서버 발급 transactionUuid로 PENDING 결제 의도를 만든다. */
    @Override
    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            Long partyId, PaymentIntentCreateRequest request) {

        Party userParty = getParty(partyId);
        Merchant merchant = getMerchant(request.merchantPartyId());

        // 결제자 wallet 행 락 - 동일 사용자의 동시 요청을 직렬화
        Wallet userWallet =
                walletRepository
                        .findByParty_IdForUpdate(partyId)
                        .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
        Wallet merchantWallet = getWallet(request.merchantPartyId());

        // 살아있는 PENDING 재사용 (10분 이내 같은 from/to/amount)
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

        // 신규 PENDING 결제 의도 저장
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

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(PAYMENT_INTENT_TTL_MINUTES);
        return PaymentIntentResponse.from(saved, merchant, expiresAt);
    }

    /** 결제 실행 - payer wallet 비관락 + DB 멱등으로 직렬화 → 은행 결제 요청 → 상태 전환. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecuteResponse executePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request) {

        // 실행 준비: 검증, 멱등성 판단, PROCESSING 전환 (별도 REQUIRES_NEW Tx)
        PaymentExecutionPreparationResult result =
                paymentStateWriter.prepareExecution(
                        userId, partyId, transactionUuid, request.paymentPin());

        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        PaymentExecutionPrepared prepared = result.prepared();

        // 은행 결제 요청 (일시적 오류 1회 재시도 → 결과 분류). 락/Tx 밖에서 호출.
        BankOutcome<PaymentResponse> outcome =
                bankCallExecutor.callBankWithRetry(
                        () -> bankClient.payment(prepared.toBankPaymentRequest()),
                        BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.PAYMENT_FAILED);

        return switch (outcome.type()) {
            case SUCCESS ->
                    paymentStateWriter.completeSuccess(
                            transactionUuid, null, null, outcome.value().confirmedAt());
            case UNKNOWN -> paymentStateWriter.markUnknown(transactionUuid);
            case TERMINAL_FAILED -> {
                paymentStateWriter.completeFailed(transactionUuid);
                throw new BusinessException(outcome.errorCode());
            }
        };
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
