package family.fisa.hangangpay.domain.transaction.service.cancel;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.internal.cancel.CancelExecutionPrepared;
import java.time.LocalDateTime;

/** CANCEL(결제 취소) 상태 쓰기 전담. 각 메서드는 REQUIRES_NEW로 독립 트랜잭션을 커밋한다. */
public interface CancelStateWriter {

    /**
     * 취소 사전 처리 - 검증 + CANCEL 저장 + 승인번호 + Processing 전환. 이 메서드가 커밋되면 Bank 호출 전까지 CANCEL 레코드가 DB에 남아
     * 있어, 네트워크 오류 시 스케줄러 복구 대상으로 인식된다.
     */
    CancelExecutionPrepared prepareCancel(
            Long merchantPartyId, Long transactionId, String paymentPin);

    PaymentCancelResponse markUnknown(String cancelTransactionUuid);

    /** Bank cancel 성공 후 CANCEL 거래를 SUCCESS로 확정한다. */
    PaymentCancelResponse completeSuccess(
            String cancelTransactionUuid,
            String txHash,
            String bankTransactionId,
            LocalDateTime confirmedAt);

    void completeFailed(String transactionUuid);

    /** 복구했지만 은행이 아직 처리 중일 때 재조정 시도 횟수를 1 올린다. (cap 진행용) */
    void incrementReconcileAttempt(String cancelTransactionUuid);

    /** 자동 복구 시도 한도를 소진한 취소를 EXPIRED 터미널로 닫는다. */
    void markExpired(String cancelTransactionUuid);

    CancelExecutionPrepared prepareReconcile(Long merchantPartyId, Long transactionId);

    PaymentCancelResponse applyReconcileResult(
            String cancelTransactionUuid, BankTransactionStatusResponse bankStatus);
}
