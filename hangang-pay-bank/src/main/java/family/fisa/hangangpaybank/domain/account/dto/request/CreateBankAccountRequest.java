package family.fisa.hangangpaybank.domain.account.dto.request;

import java.math.BigDecimal;

public record CreateBankAccountRequest(
        Long institutionId, String accountNumber, String ownerName, BigDecimal initialBalance) {}
