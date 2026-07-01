package family.fisa.hangangpay.client.bank.dto.response;

import java.math.BigDecimal;

public record BankAccountResponse(
        Long id, Long institutionId, String accountNumber, BigDecimal balance, String ownerName) {}
