package family.fisa.hangangpay.domain.transaction.service.payment;

import family.fisa.hangangpay.domain.merchant.dto.response.MerchantPaymentDetailResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentDetail;
import family.fisa.hangangpay.domain.transaction.dto.user.response.MerchantPaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.UserPaymentHistoryDetail;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;

/** PAYMENT(결제) 조회 전용. 취소(CANCEL)는 결제 내역/상세에 흡수해 함께 조회한다. */
public interface PaymentQueryService {

    /** 사용자 결제 내역(PAYMENT/CANCEL) 커서 페이지 */
    CursorPageResponse<PaymentHistoryItem> getUserPaymentHistory(
            Long partyId, CursorPageRequest request, int size);

    /** 가맹점 결제 내역 커서 페이지 */
    CursorPageResponse<MerchantPaymentHistoryItem> getMerchantPaymentHistory(
            Long partyId, CursorPageRequest request, int size);

    /** 사용자 결제 상세 내역 */
    UserPaymentHistoryDetail getUserPaymentHistoryDetail(Long partyId, Long transactionId);

    /** 가맹점 결제 상세 내역 */
    MerchantPaymentDetailResponse<MerchantPaymentDetail> getMerchantPaymentDetail(
            Long partyId, Long transactionId);
}
