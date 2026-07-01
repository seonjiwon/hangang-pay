package family.fisa.hangangpay.client.bank.dto.request;

import java.math.BigDecimal;

public record BankAccountCreateRequest(
        Long institutionId, String accountNumber, String ownerName, BigDecimal initialBalance) {}
