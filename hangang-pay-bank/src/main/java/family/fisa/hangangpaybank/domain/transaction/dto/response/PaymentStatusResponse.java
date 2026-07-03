package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import java.time.LocalDateTime;

public record PaymentStatusResponse(
        String transactionUuid, Long bankTransactionId, String status, LocalDateTime confirmedAt) {

    public static PaymentStatusResponse of(String transactionUuid, WalletLedger ledger) {
        return new PaymentStatusResponse(
                transactionUuid,
                ledger.getId(),
                mapStatus(ledger.getStatus()),
                ledger.getConfirmedAt());
    }

    private static String mapStatus(WalletLedgerStatus status) {
        return switch (status) {
            case SUCCESS -> "SUCCESS";
            case FAILED -> "FAILED";
            case PENDING -> "PROCESSING";
        };
    }
}
