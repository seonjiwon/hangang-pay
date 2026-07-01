package family.fisa.hangangpay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 계좌 1원 인증 발송 요청 DTO */
public record AccountSendRequest(
        /** 계좌번호 */
        @NotBlank String accountNumber,
        /** 기관 식별자 */
        @NotNull Long institutionId) {}
