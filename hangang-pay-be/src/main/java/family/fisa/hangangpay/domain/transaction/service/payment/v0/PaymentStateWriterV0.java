package family.fisa.hangangpay.domain.transaction.service.payment.v0;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.ApprovalNumberGenerator;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecisionType;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPrepared;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.repository.TransactionRepository;
import family.fisa.hangangpay.domain.transaction.service.payment.PaymentStateWriter;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.code.WalletErrorCode;
import family.fisa.hangangpay.domain.wallet.repository.WalletRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAYMENT 상태 쓰기 전담. 각 메서드는 REQUIRES_NEW로 독립 트랜잭션을 커밋한다.
 *
 * <p>동시 실행은 payer wallet 비관적 락(NOWAIT)으로 직렬화하고, 멱등성은 멱등 스토어가 담당한다.
 */
@Slf4j
// @Service  // [벤치마크] v1 실험 중 비활성
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentStateWriterV0 implements PaymentStateWriter {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final PaymentIdempotencyStore paymentIdempotencyStore;

    public PaymentExecutionPreparationResult prepareExecution(
            Long userId, Long partyId, String transactionUuid, String paymentPin) {

        // 1. payer wallet 행에 NOWAIT 락을 건다. 다른 실행이 이미 락을 쥐고 있으면 대기 없이 즉시 실패 -> 중복 실행 거절.
        try {
            walletRepository
                    .findByParty_IdForUpdateNoWait(partyId)
                    .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));
        } catch (PessimisticLockingFailureException e) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
        }

        // 2. PENDING 결제 거래와 결제자(PIN 검증용)를 조회한다.
        Transaction transaction = getPaymentTransaction(transactionUuid);
        User user = getUser(userId);

        // 3. 이 거래가 요청자 본인 것인지 확인한다.
        transaction.validateOwner(partyId);

        // 4. 결제 PIN을 검증한다.
        if (!passwordEncoder.matches(paymentPin, user.getPaymentPinHash())) {
            throw new BusinessException(UserErrorCode.INVALID_PIN_NUMBER);
        }

        // 5. 멱등 스토어에 실행을 선점한다. 같은 uuid의 재요청이면 그 결과로 분기한다.
        IdempotencyDecision<PaymentExecuteResponse> decision =
                paymentIdempotencyStore.beginExecution(
                        new IdempotencyKey(transactionUuid, null), transaction.getId());

        // 5-1. 이미 완료된 요청이면 저장된 응답(snapshot)을 그대로 반환한다.
        if (decision.type() == IdempotencyDecisionType.RETURN_SNAPSHOT) {
            return PaymentExecutionPreparationResult.snapshot(decision.responseSnapshot());
        }
        // 5-2. 같은 uuid인데 요청 내용이 다르면 충돌로 거절한다.
        if (decision.type() == IdempotencyDecisionType.CONFLICT) {
            throw new BusinessException(TransactionErrorCode.IDEMPOTENCY_CONFLICT);
        }
        // 5-3. 아직 처리 중인 요청이면 거절한다.
        if (decision.type() == IdempotencyDecisionType.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
        }

        // 6. 실행 가능한 상태(PENDING)인지 확인하고 PROCESSING으로 전환한다.
        transaction.validateExecutableStatus();
        transaction.markProcessing();

        // 7. 은행 호출에 필요한 실행 데이터를 만들어 반환한다.
        return PaymentExecutionPreparationResult.prepared(
                PaymentExecutionPrepared.from(transaction, null));
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
        Transaction transaction = getPaymentTransaction(transactionUuid);
        Merchant merchant = getMerchant(transaction.getToParty().getId());

        transaction.markSuccess(txHash, bankTransactionId);
        transaction.assignApprovalNumber(ApprovalNumberGenerator.generate(transaction.getId()));

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

        Merchant merchant = getMerchant(transaction.getToParty().getId());
        return PaymentExecuteResponse.from(
                transaction, merchant.getMerchantName(), bankStatus.confirmedAt());
    }

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

    private void validateBankSuccessReconcileResult(BankTransactionStatusResponse bankStatus) {
        if (bankStatus.bankTransactionId() == null) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_RECOVERY_RESULT_INVALID);
        }
    }
}
