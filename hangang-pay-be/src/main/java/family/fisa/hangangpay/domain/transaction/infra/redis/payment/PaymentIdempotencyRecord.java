package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;

public record PaymentIdempotencyRecord(
        String transactionUuid,
        String requestHash,
        TransactionStatus status,
        Long transactionId,
        PaymentExecuteResponse responseSnapshot) {

    public static PaymentIdempotencyRecord processing(
            String transactionUuid, String requestHash, Long transactionId) {
        return new PaymentIdempotencyRecord(
                transactionUuid, requestHash, TransactionStatus.PROCESSING, transactionId, null);
    }

    public PaymentIdempotencyRecord complete(PaymentExecuteResponse responseSnapshot) {
        return new PaymentIdempotencyRecord(
                transactionUuid,
                requestHash,
                responseSnapshot.status(),
                transactionId,
                responseSnapshot);
    }

    public PaymentIdempotencyRecord withStatus(TransactionStatus status) {
        return new PaymentIdempotencyRecord(
                transactionUuid, requestHash, status, transactionId, responseSnapshot);
    }

    public PaymentIdempotencyRecord fail() {
        return new PaymentIdempotencyRecord(
                transactionUuid, requestHash, TransactionStatus.FAILED, transactionId, null);
    }
}
