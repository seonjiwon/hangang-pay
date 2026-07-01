package family.fisa.hangangpay.domain.merchant.dto.response;

import family.fisa.hangangpay.domain.account.entity.Account;

public record MerchantSettlementAccountItem(
        Long accountId, String institutionName, String accountNumber, String accountType) {
    public static MerchantSettlementAccountItem from(Account account) {
        return new MerchantSettlementAccountItem(
                account.getId(),
                account.getInstitution().getInstitutionName(),
                account.getAccountNumber(),
                account.getAccountType().name());
    }
}
