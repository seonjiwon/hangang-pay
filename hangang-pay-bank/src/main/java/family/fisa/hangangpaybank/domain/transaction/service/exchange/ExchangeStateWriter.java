package family.fisa.hangangpaybank.domain.transaction.service.exchange;

import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;

/** 환전 흐름의 메인 트랜잭션 및 실패 보상 포트. 현재 구현은 {@code v1.ExchangeStateWriterV1}. */
public interface ExchangeStateWriter {

    ExchangeResponse executeExchange(ExchangeRequest request);

    void saveFailedAccountLedger(ExchangeRequest request);
}
