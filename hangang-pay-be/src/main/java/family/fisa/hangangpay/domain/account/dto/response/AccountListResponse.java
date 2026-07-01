package family.fisa.hangangpay.domain.account.dto.response;

import java.util.List;

/** 계좌 목록 조회 응답 래퍼 */
public record AccountListResponse(
        /** 계좌 항목 목록 */
        List<AccountResponse> accounts,
        /** 전체 계좌 수 */
        int totalCount) {}
