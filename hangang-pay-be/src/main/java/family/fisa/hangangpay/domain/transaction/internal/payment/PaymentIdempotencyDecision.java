package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;

public record PaymentIdempotencyDecision(
        PaymentIdempotencyDecisionType type, PaymentExecuteResponse responseSnapshot) {

    public static PaymentIdempotencyDecision newRequest() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.NEW_REQUEST, null);
    }

    public static PaymentIdempotencyDecision returnSnapshot(
            PaymentExecuteResponse responseSnapshot) {
        return new PaymentIdempotencyDecision(
                PaymentIdempotencyDecisionType.RETURN_SNAPSHOT, responseSnapshot);
    }

    public static PaymentIdempotencyDecision processing() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.PROCESSING, null);
    }

    public static PaymentIdempotencyDecision conflict() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.CONFLICT, null);
    }

    // 이미 실패로 끝난 요청. snapshot은 없다(FAILED는 본문 응답이 아니라 예외로 내려감).
    public static PaymentIdempotencyDecision alreadyFailed() {
        return new PaymentIdempotencyDecision(PaymentIdempotencyDecisionType.ALREADY_FAILED, null);
    }
}
