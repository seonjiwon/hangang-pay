package family.fisa.hangangpay.domain.account.dto.response;

/** 주거래 계좌 변경 응답 DTO */
public record PrimaryAccountResponse(
        /** 새로 지정된 주거래 계좌 식별자 */
        Long accountId,
        /** 계좌 유형 - 항상 PRIMARY */
        String accountType,
        /** 변경 전 주거래 계좌 식별자, 이미 주거래인 경우 null */
        Long previousPrimaryAccountId) {}
