package family.fisa.hangangpay.domain.transaction.internal.cancel;

import family.fisa.hangangpay.client.bank.dto.request.CancelRequest;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;

public record CancelExecutionPrepared(
        String cancelTransactionUuid,
        String originalTransactionUuid,
        String fromWalletAddress, // 가맹점 지갑 (취소 출발)
        String toWalletAddress, // 소비자 지갑 (취소 도착)
        BigDecimal amount) {
    /** Bank Cancel 요청 객체로 변환 */
    public CancelRequest toBankCancelRequest() {
        return new CancelRequest(
                cancelTransactionUuid,
                originalTransactionUuid,
                fromWalletAddress,
                toWalletAddress,
                amount);
    }

    public static CancelExecutionPrepared from(Transaction original, Transaction saved) {
        return new CancelExecutionPrepared(
                saved.getTransactionUuid(),
                original.getTransactionUuid(),
                original.getToWallet().getAddress(),
                original.getFromWallet().getAddress(),
                original.getAmount());
    }
}
