package family.fisa.hangangpaybank.domain.transaction.service.exchange;

import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeStatusResponse;

/** 환전 상태 조회 포트. 현재 구현은 {@code v1.ExchangeQueryServiceV1}. */
public interface ExchangeQueryService {

    ExchangeStatusResponse getStatus(String transactionUuid);
}
