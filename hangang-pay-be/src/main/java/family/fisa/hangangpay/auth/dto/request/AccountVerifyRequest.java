package family.fisa.hangangpay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 계좌 1원 인증 검증 요청 DTO */
public record AccountVerifyRequest(
        /** 계좌번호 */
        @NotBlank String accountNumber,
        /** 기관 식별자 */
        @NotNull Long institutionId,
        /** 인증 코드 */
        @NotBlank String code) {}
