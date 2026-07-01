package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.client.bank.dto.request.PaymentRequest;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import java.math.BigDecimal;

public record PaymentExecutionPrepared(
        String transactionUuid,
        String requestHash,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {

    public static PaymentExecutionPrepared from(Transaction t, String requestHash) {
        return new PaymentExecutionPrepared(
                t.getTransactionUuid(),
                requestHash,
                t.getFromWallet().getAddress(),
                t.getToWallet().getAddress(),
                t.getAmount());
    }

    public PaymentRequest toBankPaymentRequest() {
        return new PaymentRequest(transactionUuid, fromWalletAddress, toWalletAddress, amount);
    }
}
