package family.fisa.hangangpay.domain.account.dto.response;

import family.fisa.hangangpay.domain.account.entity.Account;

public record MerchantAccountUpdateResponse(
        Long accountId, String institutionCode, String institutionName, String accountNumber) {

    public static MerchantAccountUpdateResponse from(Account account) {
        return new MerchantAccountUpdateResponse(
                account.getId(),
                account.getInstitution().getInstitutionCode(),
                account.getInstitution().getInstitutionName(),
                account.getAccountNumber());
    }
}
