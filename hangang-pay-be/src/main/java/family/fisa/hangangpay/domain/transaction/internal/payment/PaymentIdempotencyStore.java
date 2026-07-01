package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;

public interface PaymentIdempotencyStore {
    PaymentIdempotencyDecision beginExecution(
            String transactionUuid, String requestHash, Long transactionId);

    // Bank 결제 완료(SUCCESS/UNKNOWN) 후 최종 응답 snapshot 저장
    void completeExecution(String transactionUuid, PaymentExecuteResponse responseSnapshot);

    // Bank가 결정적으로 거부했거나(복구 결과 FAILED) 은행 미도달(404)일 때 record를 FAILED로 마킹한다.
    // 이후 동일 transactionUuid 재요청은 ALREADY_FAILED로 즉시 거절된다.
    void failExecution(String transactionUuid);
}
