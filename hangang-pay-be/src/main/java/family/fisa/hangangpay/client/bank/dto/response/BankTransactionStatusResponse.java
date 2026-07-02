package family.fisa.hangangpay.client.bank.dto.response;

import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;

public record BankTransactionStatusResponse(
        String transactionUuid,
        Long bankTransactionId,
        TransactionStatus status,
        String txHash, // exchange 상태 조회 시 존재. payment/cancel 상태 조회 시 null (blockchain async)
        LocalDateTime confirmedAt) {

    public static BankTransactionStatusResponse failed(String reconcileUuid) {
        return new BankTransactionStatusResponse(
                reconcileUuid, null, TransactionStatus.FAILED, null, null);
    }
}
