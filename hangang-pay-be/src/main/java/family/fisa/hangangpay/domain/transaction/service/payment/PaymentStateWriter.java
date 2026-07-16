package family.fisa.hangangpay.domain.transaction.service.payment;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentExecutionPreparationResult;
import java.time.LocalDateTime;

/** PAYMENT 상태 쓰기 전담. 각 메서드는 REQUIRES_NEW로 독립 트랜잭션을 커밋한다. */
public interface PaymentStateWriter {

    /** PIN(BCrypt) 검증. 상태-쓰기 트랜잭션 밖에서 호출해 느린 BCrypt가 DB 커넥션을 쥐지 않게 한다. */
    void verifyPaymentPin(Long userId, String paymentPin);

    PaymentExecutionPreparationResult prepareExecution(
            Long userId, Long partyId, String transactionUuid, String paymentPin);

    PaymentExecuteResponse markUnknown(String transactionUuid);

    PaymentExecuteResponse completeSuccess(
            String transactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt);

    /** Bank가 결정적으로 거부한 경우 결제를 FAILED로 확정한다. */
    void completeFailed(String transactionUuid);

    /** 복구했지만 은행이 아직 처리 중일 때 재조정 시도 횟수를 1 올린다. (cap 진행용) */
    void incrementReconcileAttempt(String transactionUuid);

    /** 자동 복구 시도 한도를 소진한 결제를 EXPIRED 터미널로 닫는다. */
    void markExpired(String transactionUuid);

    String prepareReconcile(Long partyId, String transactionUuid);

    PaymentExecuteResponse applyReconcileResult(
            String transactionUuid, BankTransactionStatusResponse bankStatus);
}
