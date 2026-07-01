package family.fisa.hangangpay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

/** SMS 인증 발송 요청 DTO */
public record SmsSendRequest(
        /** 수신 휴대폰 번호 */
        @NotBlank String phoneNumber) {}
