package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
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
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

@Service
@RequiredArgsConstructor
public class PaymentCommandServiceV1 implements PaymentCommandService {

    private static final long PAYMENT_INTENT_TTL_MINUTES = 10L;
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
    private final PaymentIdempotencyStore paymentIdempotencyStore;
    private final PaymentIntentDedupStore paymentIntentDedupStore;
    private final PaymentLockManager paymentLockManager;
    private final PaymentRateLimiter paymentRateLimiter;
    private final PaymentRequestHashGenerator paymentRequestHashGenerator;
    private final PaymentStateWriter paymentStateWriter;
    private final BankCallExecutor bankCallExecutor;

    @Override
    @Transactional
    public PaymentIntentResponse createPaymentIntent(
            Long partyId, PaymentIntentCreateRequest request) {

        /** 요청을 처리하기 전, TokenBucket 방식을 이용하여 Quota 확인 */
        paymentRateLimiter.checkIntentRateLimit(partyId, request.merchantPartyId());

        /** DB 조회 */
        Party userParty = getParty(partyId);
        Merchant merchant = getMerchant(request.merchantPartyId());
        Wallet userWallet = getWallet(partyId);
        Wallet merchantWallet = getWallet(request.merchantPartyId());

        // 1. 살아있는 PENDING 재사용 (DB가 진실 = 30초~10분 구간도 정확히 재사용)
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

        /** 결제 실행 전에 서버 발급 transactionUuid로 PENDING 결제 의도를 생성한다 */
        String fingerprint =
                paymentRequestHashGenerator.generateIntentExecutionHash(
                        userParty.getId(), merchant.getParty().getId(), request.amount());
        String transactionUuid = UUID.randomUUID().toString();
        Optional<String> existingTransactionUuid =
                paymentIntentDedupStore.reserve(fingerprint, transactionUuid);

        if (existingTransactionUuid.isPresent()) {
            Optional<Transaction> existing =
                    transactionRepository.findByTransactionUuid(existingTransactionUuid.get());

            if (existing.isPresent()) {
                LocalDateTime expiresAt = intentExpiresAt(existing.get());
                return PaymentIntentResponse.from(existing.get(), merchant, expiresAt);
            }
        }

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

    /** Propagation.NOT_SUPPORTED: 트랜잭션 없이 실행 */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecuteResponse executePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request) {

        return paymentLockManager.withTransactionLock(
                transactionUuid,
                () ->
                        doExecutePayment(
                                userId, partyId, transactionUuid, request)); // 콜백으로 락 걸고 이어서 수행
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PaymentExecuteResponse recoverPayment(Long partyId, String transactionUuid) {
        return paymentLockManager.withTransactionLock(
                transactionUuid, () -> doRecoverPayment(partyId, transactionUuid));
    }

    /** 내부 메소드 */
    private PaymentExecuteResponse doExecutePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request) {

        /** 1. 거래 조회/검증, PROCESSING 저장 */
        PaymentExecutionPreparationResult result =
                paymentStateWriter.prepareExecution(
                        userId, partyId, transactionUuid, request.paymentPin());

        if (result.hasSnapshot()) {
            return result.responseSnapshot();
        }

        PaymentExecutionPrepared prepared = result.prepared();

        /** 2. Bank 외부 호출 */
        BankOutcome<PaymentResponse> outcome =
                bankCallExecutor.callBankWithRetry(
                        () -> bankClient.payment(prepared.toBankPaymentRequest()),
                        BANK_FAIL_CODE_MAP,
                        TransactionErrorCode.PAYMENT_FAILED);

        /** 3. 결과 분류 및 상태 반영 */
        PaymentExecuteResponse response =
                switch (outcome.type()) {
                    case SUCCESS ->
                            paymentStateWriter.completeSuccess(
                                    transactionUuid, null, null, outcome.value().confirmedAt());
                    case UNKNOWN -> paymentStateWriter.markUnknown(transactionUuid);
                    case TERMINAL_FAILED -> {
                        paymentStateWriter.completeFailed(transactionUuid);
                        throw new BusinessException(
                                outcome.errorCode()); // GlobalExceptionHandler 감지
                    }
                };

        /** 4. Redis용 idempotency snapshot 저장 */
        paymentIdempotencyStore.completeExecution(transactionUuid, response);

        return response;
    }

    private PaymentExecuteResponse doRecoverPayment(Long partyId, String transactionUuid) {
        // 1. 복구 대상 검증 + 복구용 uuid 확보 (UNKNOWN / PROCESSING)
        String recoveryUuid = paymentStateWriter.prepareRecovery(partyId, transactionUuid);

        // 2. Bank 조회로 결과 확정 (404은 은행 미도달로 간주 -> FAILED 처리)
        PaymentExecuteResponse response = resolvePaymentRecovery(recoveryUuid);

        // 3. 종단으로 끝났다면, Redis 멱등 record도 정리한다. -> 고아 상태인 PROCESSING 청소
        if (response.status() == TransactionStatus.SUCCESS) {
            paymentIdempotencyStore.completeExecution(recoveryUuid, response);
        } else if (response.status() == TransactionStatus.FAILED) {
            paymentIdempotencyStore.failExecution(recoveryUuid);
        } else {
            // 은행이 아직 처리 중 → 시도 횟수만 올리고 다음 주기 재시도 (cap 도달 시 sweep 제외)
            paymentStateWriter.incrementRecoveryAttempt(recoveryUuid);
        }

        return response;
    }

    /**
     * bankClient 호출 전 종료된 요청들은 PROCESSING 레코드가 저장되고 고아상태에 빠진다. 이런 경우는 은행쪽에 조회 응답이 404 - NOT FOUND로
     * 반환 된다.
     */
    private PaymentExecuteResponse resolvePaymentRecovery(String recoveryUuid) {
        try {
            // 1. 정상 조회: SUCCESS/FAILED/PROCESSING을 applyRecoveryResult가 반영한다.
            BankTransactionStatusResponse bankStatus =
                    bankClient.getTransactionStatus(recoveryUuid);
            return paymentStateWriter.applyRecoveryResult(recoveryUuid, bankStatus);
        } catch (RestClientResponseException e) {
            // 2. 404가 아니면 (5xx 등) 일시적 오류 같은 경우 다시 던져서 다음 sweep에 재시도한다.
            if (!e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw e;
            }
            // 3. 404는 은행 DB 원장에 기록자체가 없다. -> 은행 도달전 사망했다는 의미로 FAILED 확정 (플랫폼의 책임)
            // applyRecoveryResult의 FIALED 처리를 그대로 재사용하기 위해 FAILED status 합성
            BankTransactionStatusResponse asFailed =
                    BankTransactionStatusResponse.failed(recoveryUuid);

            return paymentStateWriter.applyRecoveryResult(recoveryUuid, asFailed);
        }
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
