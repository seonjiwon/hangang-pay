package family.fisa.hangangpay.domain.merchant.dto.response;

import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;

public record MerchantMyPageResponse(
        Long merchantId,
        Long partyId,
        String merchantName,
        String businessNumber,
        String ownerName,
        String phoneNumber,
        String address,
        MerchantSettlementAccountItem settlementAccount) {
    public static MerchantMyPageResponse from(Merchant merchant, Account account) {
        return new MerchantMyPageResponse(
                merchant.getId(),
                merchant.getParty().getId(),
                merchant.getMerchantName(),
                merchant.getBusinessNumber(),
                merchant.getOwnerName(),
                merchant.getPhoneNumber(),
                merchant.getAddress(),
                MerchantSettlementAccountItem.from(account));
    }
}
