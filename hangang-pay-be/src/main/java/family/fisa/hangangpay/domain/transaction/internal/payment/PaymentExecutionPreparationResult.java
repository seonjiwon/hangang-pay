package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;

public record PaymentExecutionPreparationResult(
        PaymentExecutionPrepared prepared, PaymentExecuteResponse responseSnapshot) {

    public static PaymentExecutionPreparationResult prepared(PaymentExecutionPrepared prepared) {
        return new PaymentExecutionPreparationResult(prepared, null);
    }

    public static PaymentExecutionPreparationResult snapshot(PaymentExecuteResponse snapshot) {
        return new PaymentExecutionPreparationResult(null, snapshot);
    }

    public boolean hasSnapshot() {
        return responseSnapshot != null;
    }
}
