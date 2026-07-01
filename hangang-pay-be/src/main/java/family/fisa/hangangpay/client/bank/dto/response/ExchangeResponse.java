package family.fisa.hangangpay.client.bank.dto.response;

import java.math.BigDecimal;

public record ExchangeResponse(
        String transactionUuid, Long bankTransactionId, String status, BigDecimal accountBalance) {}
