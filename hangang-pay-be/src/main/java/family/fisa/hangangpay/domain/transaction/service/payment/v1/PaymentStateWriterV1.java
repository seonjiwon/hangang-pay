package family.fisa.hangangpay.domain.transaction.service.payment.v1;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.payment.*;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentStateWriterV1 implements PaymentStateWriter {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentIdempotencyStore paymentIdempotencyStore;
    private final PaymentRateLimiter paymentRateLimiter;
    private final PaymentRequestHashGenerator paymentRequestHashGenerator;

    public PaymentExecutionPreparationResult prepareExecution(
            Long userId, Long partyId, String transactionUuid, String paymentPin) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        User user = getUser(userId);

        transaction.validateOwner(partyId);

        if (!passwordEncoder.matches(paymentPin, user.getPaymentPinHash())) {
            throw new BusinessException(UserErrorCode.INVALID_PIN_NUMBER);
        }

        String requestHash = paymentRequestHashGenerator.generatePaymentExecuteHash(transaction);

        PaymentIdempotencyDecision decision =
                paymentIdempotencyStore.beginExecution(
                        transactionUuid, requestHash, transaction.getId());

        if (decision.type() == PaymentIdempotencyDecisionType.RETURN_SNAPSHOT) {
            return PaymentExecutionPreparationResult.snapshot(decision.responseSnapshot());
        }

        if (decision.type() == PaymentIdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }

        if (decision.type() == PaymentIdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
        }

        transaction.validateExecutableStatus();

        paymentRateLimiter.checkExecutionRateLimit(
                partyId, transaction.getToParty().getId(), transactionUuid);

        paymentRateLimiter.checkBankOutboundRateLimit();

        transaction.markProcessing();

        return PaymentExecutionPreparationResult.prepared(
                PaymentExecutionPrepared.from(transaction, requestHash));
    }

    public PaymentExecuteResponse markUnknown(String transactionUuid) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        transaction.markUnknown();

        return PaymentExecuteResponse.from(transaction, null, LocalDateTime.now());
    }

    public PaymentExecuteResponse completeSuccess(
            String transactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt) {
        // 1. PROCESSING 상태 거래 조회
        Transaction transaction = getPaymentTransaction(transactionUuid);
        Merchant merchant = getMerchant(transaction.getToParty().getId());

        // 2. txHash, bankTransactionId 기록 후 SUCCESS 전환
        transaction.markSuccess(txHash, bankTransactionId);

        // 3. 승인번호 생성 — id는 createPaymentIntent 시점에 이미 채번됨
        transaction.assignApprovalNumber(makeApvNumber(transaction.getId()));

        return PaymentExecuteResponse.from(transaction, merchant.getMerchantName(), confirmedAt);
    }

    /** Bank가 결정적으로 거부한 경우 결제를 FAILED로 확정한다. */
    public void completeFailed(String transactionUuid) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        transaction.markFailed();
        log.warn("결제 실패 확정(FAILED). transactionUuid={}", transactionUuid);
    }

    /** 복구했지만 은행이 아직 처리 중일 때 재조정 시도 횟수를 1 올린다. (cap 진행용) */
    public void incrementReconcileAttempt(String transactionUuid) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        transaction.incrementReconcileAttempt();
    }

    /** 자동 복구 시도 한도를 소진한 결제를 EXPIRED 터미널로 닫는다. */
    public void markExpired(String transactionUuid) {
        Transaction transaction = getPaymentTransaction(transactionUuid);
        transaction.markExpired();
    }

    public String prepareReconcile(Long partyId, String transactionUuid) {
        Transaction transaction = getPaymentTransaction(transactionUuid);

        transaction.validateOwner(partyId);
        transaction.validateReconcilableStatus();

        paymentRateLimiter.checkReconcileRateLimit(partyId, transactionUuid);
        paymentRateLimiter.checkBankOutboundRateLimit();

        return transaction.getTransactionUuid();
    }

    public PaymentExecuteResponse applyReconcileResult(
            String transactionUuid, BankTransactionStatusResponse bankStatus) {
        Transaction transaction = getPaymentTransaction(transactionUuid);

        if (bankStatus.status() == TransactionStatus.SUCCESS) {
            validateBankSuccessReconcileResult(bankStatus);
            transaction.reconcileSuccess(null, String.valueOf(bankStatus.bankTransactionId()));
        }

        if (bankStatus.status() == TransactionStatus.FAILED) {
            transaction.reconcileFailed();
        }

        // PROCESSING(은행 아직 처리 중)이면 상태를 바꾸지 않고 현재 상태(UNKNOWN/PROCESSING) 그대로 반환한다.
        // → 복구 미완. 다음 복구/sweep에서 재시도된다. (별도 분기 불필요)

        Merchant merchant = getMerchant(transaction.getToParty().getId());

        return PaymentExecuteResponse.from(
                transaction, merchant.getMerchantName(), bankStatus.confirmedAt());
    }

    /** 내부 메소드 */
    private User getUser(Long userId) {
        return userRepository
                .findByIdWithParty(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }

    private Transaction getPaymentTransaction(String transactionUuid) {
        return transactionRepository
                .findByTransactionUuid(transactionUuid)
                .orElseThrow(() -> new BusinessException(TransactionErrorCode.PAYMENT_NOT_FOUND));
    }

    private Merchant getMerchant(Long partyId) {
        return merchantRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));
    }

    private String makeApvNumber(Long id) {
        return "APV-" + LocalDateTime.now().getYear() + "-" + String.format("%08d", id);
    }

    private void validateBankSuccessReconcileResult(BankTransactionStatusResponse bankStatus) {
        if (bankStatus.bankTransactionId() == null) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
        }
    }
}
