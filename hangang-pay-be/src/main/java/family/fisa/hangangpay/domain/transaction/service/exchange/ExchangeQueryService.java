package family.fisa.hangangpay.domain.transaction.service.exchange;

import family.fisa.hangangpay.domain.merchant.dto.response.MerchantSettlementHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeHistoryItem;
import family.fisa.hangangpay.domain.transaction.dto.user.response.ExchangeInitResponse;
import family.fisa.hangangpay.domain.transaction.dto.user.response.UserExchangeHistoryDetail;
import family.fisa.hangangpay.global.pagination.CursorPageRequest;
import family.fisa.hangangpay.global.pagination.CursorPageResponse;

/** EXCHANGE(환전) 조회 전용. 정산(가맹점) 내역도 EXCHANGE 기록으로 함께 조회한다. */
public interface ExchangeQueryService {

    ExchangeInitResponse getExchangeInit(Long partyId);

    /** 환전 자격(최근 충전액의 60% 이상 사용) 여부. ExchangeCommandService도 사용. */
    boolean checkEligibility(Long partyId);

    /** 사용자 환전 내역 커서 페이지 */
    CursorPageResponse<ExchangeHistoryItem> getExchangeHistories(
            Long partyId, CursorPageRequest request, int size);

    /** 사용자 환전 상세 내역 */
    UserExchangeHistoryDetail getUserExchangeHistoryDetail(Long partyId, Long transactionId);

    /** 가맹점 정산 내역(EXCHANGE) 커서 페이지 */
    CursorPageResponse<MerchantSettlementHistoryItem> getMerchantSettlementHistory(
            Long partyId, CursorPageRequest cursor, int size);
}
