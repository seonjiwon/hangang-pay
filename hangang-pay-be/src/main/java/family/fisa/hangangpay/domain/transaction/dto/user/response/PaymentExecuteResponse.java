package family.fisa.hangangpay.domain.transaction.dto.user.response;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentExecuteResponse(
        String transactionUuid,
        TransactionStatus status,
        String approvalNumber,
        BigDecimal amount,
        String merchantName,
        LocalDateTime confirmedAt) {

    public static PaymentExecuteResponse from(
            Transaction t, String merchantName, LocalDateTime confirmedAt) {
        return new PaymentExecuteResponse(
                t.getTransactionUuid(),
                t.getStatus(),
                t.getApprovalNumber(),
                t.getAmount(),
                merchantName,
                confirmedAt);
    }
}
