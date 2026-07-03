package family.fisa.hangangpaybank.domain.account.dto.response;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import java.math.BigDecimal;

public record BankAccountResponse(
        Long id, Long institutionId, String accountNumber, BigDecimal balance, String ownerName) {

    public static BankAccountResponse from(BankAccount bankAccount) {
        // 1. BankAccount Entity의 핵심 필드만 노출
        return new BankAccountResponse(
                bankAccount.getId(),
                bankAccount.getInstitution().getId(),
                bankAccount.getAccountNumber(),
                bankAccount.getBalance(),
                bankAccount.getOwnerName());
    }
}
