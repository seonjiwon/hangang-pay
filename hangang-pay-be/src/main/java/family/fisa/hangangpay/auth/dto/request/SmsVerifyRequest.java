package family.fisa.hangangpay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** SMS 인증 검증 요청 DTO */
public record SmsVerifyRequest(
        /** 수신 휴대폰 번호 */
        @NotBlank String phoneNumber,
        /** 인증 코드 */
        @NotBlank String code) {}
