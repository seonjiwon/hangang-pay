package family.fisa.hangangpaybank.domain.transaction.service.exchange;

import family.fisa.hangangpaybank.domain.transaction.dto.request.ExchangeRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeResponse;

/** 환전 커맨드(오케스트레이터) 포트. 현재 구현은 {@code v1.ExchangeCommandServiceV1}. */
public interface ExchangeCommandService {

    ExchangeResponse exchange(ExchangeRequest request);
}
