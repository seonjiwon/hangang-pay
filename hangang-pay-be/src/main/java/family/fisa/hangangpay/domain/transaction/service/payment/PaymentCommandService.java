package family.fisa.hangangpay.domain.transaction.service.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentExecuteRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentIntentCreateRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentIntentResponse;

/** PAYMENT(결제) 명령 오케스트레이터. */
public interface PaymentCommandService {

    /** 결제 intent 생성 - 서버 발급 transactionUuid로 PENDING 결제 의도를 만든다. */
    PaymentIntentResponse createPaymentIntent(Long partyId, PaymentIntentCreateRequest request);

    /** 결제 실행 - 분산 락 확보 후 멱등 판단 → 은행 결제 요청 → 상태 전환. */
    PaymentExecuteResponse executePayment(
            Long userId, Long partyId, String transactionUuid, PaymentExecuteRequest request);

    /** UNKNOWN/PROCESSING 결제를 은행 재조회로 복구한다. */
    PaymentExecuteResponse recoverPayment(Long partyId, String transactionUuid);
}
