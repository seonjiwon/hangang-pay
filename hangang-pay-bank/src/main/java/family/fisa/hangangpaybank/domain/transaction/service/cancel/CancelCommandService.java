package family.fisa.hangangpaybank.domain.transaction.service.cancel;

import family.fisa.hangangpaybank.domain.transaction.dto.request.CancelRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.CancelResponse;

/** 결제 취소 커맨드(오케스트레이터) 포트. 현재 구현은 {@code v1.CancelCommandServiceV1}. */
public interface CancelCommandService {

    CancelResponse cancel(CancelRequest request);
}
