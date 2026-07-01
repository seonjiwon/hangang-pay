package family.fisa.hangangpay.client.bank.dto.response;

import java.math.BigDecimal;

public record BankWalletResponse(
        Long id, Long institutionId, String walletAddress, BigDecimal balance) {}
