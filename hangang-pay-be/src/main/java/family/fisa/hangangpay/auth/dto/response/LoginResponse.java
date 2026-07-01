package family.fisa.hangangpay.auth.dto.response;

import family.fisa.hangangpay.domain.party.entity.PartyType;

public record LoginResponse(Long principalId, Long partyId, PartyType role) {}
// TODO: Id 항목들 불필요 - 추후 제거 고려
