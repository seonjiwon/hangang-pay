package family.fisa.hangangpay.client.bank.dto.response;

import family.fisa.hangangpay.client.bank.dto.MerchantRedeemAccountInfo;
import family.fisa.hangangpay.domain.account.entity.Account;
import java.math.BigDecimal;
import lombok.Builder;

@Builder
public record MerchantRedeemInitResponse(
        BigDecimal availableAmount, MerchantRedeemAccountInfo settlementAccount) {
    public static MerchantRedeemInitResponse from(BigDecimal availableAmount, Account account) {
        return MerchantRedeemInitResponse.builder()
                .availableAmount(availableAmount)
                .settlementAccount(MerchantRedeemAccountInfo.from(account))
                .build();
    }
}
