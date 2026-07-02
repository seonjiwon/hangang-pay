package family.fisa.hangangpay.domain.transaction.service.payment;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;

/**
 * UNKNOWN/PROCESSING 결제를 은행 재조회로 확정하는 reconcile 전담 서비스.
 *
 * <p>{@link family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService}와
 * 같은 패턴이되, 결제는 사용자 대면 수동 {@code /recover} 엔드포인트가 있어 소유권·rate-limit을 동반하는 수동 진입점을 추가로 갖는다.
 */
public interface PaymentReconcileService {

    /** 배치용: 스케줄러가 고른 대상을 bank 재조회로 한 건 확정한다(소유권·rate-limit 없음, 락만). */
    void reconcile(Transaction tx);

    /** 수동용: 소유권·recoverable·rate-limit 검증 후 reconcile 한다({@code /recover} 엔드포인트). */
    PaymentExecuteResponse reconcilePayment(Long partyId, String transactionUuid);
}
