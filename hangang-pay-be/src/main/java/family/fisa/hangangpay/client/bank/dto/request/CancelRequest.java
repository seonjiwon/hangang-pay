package family.fisa.hangangpay.client.bank.dto.request;

import java.math.BigDecimal;

public record CancelRequest(
        String transactionUuid,
        String originalTransactionUuid,
        String fromWalletAddress,
        String toWalletAddress,
        BigDecimal amount) {}
