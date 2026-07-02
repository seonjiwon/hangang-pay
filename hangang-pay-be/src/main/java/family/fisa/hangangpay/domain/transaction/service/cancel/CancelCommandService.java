package family.fisa.hangangpay.domain.transaction.service.cancel;

import family.fisa.hangangpay.domain.transaction.dto.user.request.PaymentCancelRequest;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;

/** CANCEL(결제 취소) 명령 오케스트레이터. 요청 주체는 가맹점. */
public interface CancelCommandService {

    /** 결제 취소 실행 - 분산 락 확보 후 멱등 판단 → 은행 취소 요청 → 상태 전환. */
    PaymentCancelResponse executeCancel(
            Long merchantPartyId, Long transactionId, PaymentCancelRequest request);
}
