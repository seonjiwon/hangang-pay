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
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPreparationResult;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPrepared;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAYMENT 상태 쓰기 전담. 각 메서드는 REQUIRES_NEW로 독립 트랜잭션을 커밋한다.
 *
 * <p>동일 사용자의 동시 실행은 payer wallet 비관적 락으로 직렬화하고, 멱등성은 거래 상태로 판단한다.
 */
@Slf4j
// @Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentStateWriterV0 implements PaymentStateWriter {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final MerchantRepository merchantRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    public PaymentExecutionPreparationResult prepareExecution(
            Long userId, Long partyId, String transactionUuid, String paymentPin) {
        // payer wallet 행 락 - 동일 사용자의 동시 실행을 직렬화 (PENDING -> PROCESSING 경합 방지)
        walletRepository
                .findByParty_IdForUpdate(partyId)
                .orElseThrow(() -> new BusinessException(WalletErrorCode.WALLET_NOT_FOUND));

        Transaction transaction = getPaymentTransaction(transactionUuid);
        User user = getUser(userId);

        transaction.validateOwner(partyId);

        if (!passwordEncoder.matches(paymentPin, user.getPaymentPinHash())) {
            throw new BusinessException(UserErrorCode.INVALID_PIN_NUMBER);
        }

        // 멱등: 거래 상태로 중복 요청 판단 (SUCCESS -> 기존 결과 재사용, PROCESSING -> 진행 중)
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            Merchant merchant = getMerchant(transaction.getToParty().getId());
            return PaymentExecutionPreparationResult.snapshot(
                    PaymentExecuteResponse.from(
                            transaction, merchant.getMerchantName(), LocalDateTime.now()));
        }
        if (transaction.getStatus() == TransactionStatus.PROCESSING) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_ALREADY_PROCESSING);
        }

        transaction.validateExecutableStatus();
        transaction.markProcessing();

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

        // PROCESSING(은행 아직 처리 중)이면 상태를 바꾸지 않고 현재 상태 그대로 반환한다.
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
