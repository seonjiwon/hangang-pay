package family.fisa.hangangpaybank.domain.transaction.service.payment;

import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentStatusResponse;

/** 결제 상태 조회 포트. 현재 구현은 {@code v1.PaymentQueryServiceV1}. */
public interface PaymentQueryService {

    PaymentStatusResponse getStatus(String transactionUuid);
}
