package family.fisa.hangangpay.domain.account.dto.request;

import jakarta.validation.constraints.NotBlank;

/** 계좌 추가 요청 DTO */
public record AccountCreateRequest(
        /** 금융기관 코드 */
        @NotBlank String institutionCode,
        /** 등록할 계좌번호 */
        @NotBlank String accountNumber) {}
