package family.fisa.hangangpaybank.domain.transaction.service.charge;

import family.fisa.hangangpaybank.domain.transaction.dto.response.ChargeStatusResponse;

/** 충전 상태 조회 포트. 현재 구현은 {@code v1.ChargeQueryServiceV1}. */
public interface ChargeQueryService {

    ChargeStatusResponse getStatus(String transactionUuid);
}
