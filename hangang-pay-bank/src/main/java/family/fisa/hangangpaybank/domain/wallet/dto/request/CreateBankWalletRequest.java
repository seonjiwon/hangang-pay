package family.fisa.hangangpaybank.domain.wallet.dto.request;

public record CreateBankWalletRequest(Long institutionId, Long partyId, boolean merchant) {}
