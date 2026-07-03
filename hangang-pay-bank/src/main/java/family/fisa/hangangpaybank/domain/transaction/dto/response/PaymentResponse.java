package family.fisa.hangangpaybank.domain.transaction.dto.response;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedgerStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 응답 DTO.
 *
 * <p>블록체인 동기화는 비동기로 처리되므로 txHash/blockNumber 같은 온체인 확인 정보는 포함하지 않는다. DB 처리 결과만 반환한다.
 */
public record PaymentResponse(
        String transactionUuid,
        String status,
        LocalDateTime confirmedAt,
        BigDecimal fromBalance,
        BigDecimal toBalance) {

    /** WalletLedger 상태와 DB 잔액으로 응답을 생성한다. */
    public static PaymentResponse from(
            String transactionUuid,
            WalletLedgerStatus ledgerStatus,
            LocalDateTime confirmedAt,
            BigDecimal fromBalance,
            BigDecimal toBalance) {
        return new PaymentResponse(
                transactionUuid, ledgerStatus.name(), confirmedAt, fromBalance, toBalance);
    }
}
