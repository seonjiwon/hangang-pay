package family.fisa.hangangpaybank.domain.transaction.service.payment;

import family.fisa.hangangpaybank.domain.transaction.dto.request.PaymentRequest;
import family.fisa.hangangpaybank.domain.transaction.dto.response.PaymentResponse;

/** 결제 커맨드(오케스트레이터) 포트. 현재 구현은 {@code v1.PaymentCommandServiceV1}. */
public interface PaymentCommandService {

    PaymentResponse payment(PaymentRequest request);
}
