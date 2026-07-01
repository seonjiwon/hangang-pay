package family.fisa.hangangpay.client.bank.dto.request;

import java.math.BigDecimal;

public record ExchangeRequest(
        String transactionUuid,
        Long institutionId,
        String walletAddress,
        String accountNumber,
        BigDecimal amount) {}
