package family.fisa.hangangpaybank.domain.wallet.dto.response;

import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import java.math.BigDecimal;

public record BankWalletResponse(
        Long id, Long institutionId, String walletAddress, BigDecimal balance) {

    public static BankWalletResponse from(BankWallet bankWallet, BigDecimal balance) {
        // 1. BankWallet Entity 핵심 필드만 노출 (encrypted_private_key는 응답에서 제외)
        return new BankWalletResponse(
                bankWallet.getId(),
                bankWallet.getInstitution().getId(),
                bankWallet.getWalletAddress(),
                balance);
    }
}
