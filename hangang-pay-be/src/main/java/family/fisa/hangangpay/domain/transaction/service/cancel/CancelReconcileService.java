package family.fisa.hangangpay.domain.transaction.service.cancel;

import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentCancelResponse;
import family.fisa.hangangpay.domain.transaction.entity.Transaction;

/**
 * UNKNOWN/PROCESSING 취소를 은행 재조회로 확정하는 reconcile 전담 서비스.
 *
 * <p>{@link family.fisa.hangangpay.domain.transaction.service.exchange.ExchangeReconcileService}와
 * 같은 패턴이되, 취소는 가맹점 대면 수동 {@code /cancel/recover} 엔드포인트가 있어 소유권 검증을 동반하는 수동 진입점을 추가로 갖는다.
 */
public interface CancelReconcileService {

    /** 배치용: 스케줄러가 고른 CANCEL 거래를 bank 재조회로 한 건 확정한다(소유권 없음, 락만). */
    void reconcile(Transaction cancelTx);

    /** 수동용: 원본 결제 소유권(가맹점=수취자) 검증 후 reconcile 한다({@code /cancel/recover} 엔드포인트). */
    PaymentCancelResponse reconcileCancel(Long merchantPartyId, Long transactionId);
}
