package family.fisa.hangangpay.auth.dto.response;

/** 인증 코드 발송 응답 DTO */
public record VerificationCodeResponse(
        /** 발급된 인증 코드 */
        String code) {}
