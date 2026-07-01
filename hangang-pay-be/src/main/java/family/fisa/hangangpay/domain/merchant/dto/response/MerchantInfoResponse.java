package family.fisa.hangangpay.domain.merchant.dto.response;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import lombok.Builder;

/** PAY-001 가맹점 정보 조회 응답 DTO. */
@Builder
public record MerchantInfoResponse(
        Long merchantId, Long partyId, String merchantName, String address, String walletAddress) {

    public static MerchantInfoResponse from(Merchant merchant, Wallet wallet) {
        return MerchantInfoResponse.builder()
                .merchantId(merchant.getId())
                .partyId(merchant.getParty().getId())
                .merchantName(merchant.getMerchantName())
                .address(merchant.getAddress())
                .walletAddress(wallet.getAddress())
                .build();
    }
}
