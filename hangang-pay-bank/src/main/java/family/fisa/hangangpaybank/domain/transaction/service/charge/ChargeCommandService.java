package family.fisa.hangangpaybank.domain.transaction.service.charge;

import family.fisa.hangangpaybank.domain.transaction.dto.request.ChargeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeResponse;

/** 충전 커맨드(오케스트레이터) 포트. 현재 구현은 {@code v1.ChargeCommandServiceV1}. */
public interface ChargeCommandService {

    ChargeResponse charge(ChargeRequest request);
}
