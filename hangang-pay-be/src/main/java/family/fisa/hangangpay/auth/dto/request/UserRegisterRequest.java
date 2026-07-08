package family.fisa.hangangpay.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 소비자 회원가입 요청 DTO */
public record UserRegisterRequest(
        /** 사용자 이름 */
        @NotBlank String name,
        /** 휴대폰 번호 */
        @NotBlank String phoneNumber,
        /** 로그인 비밀번호 */
        @NotBlank String password,
        /** 결제 PIN */
        @NotBlank @Pattern(regexp = "\\d{6}") String paymentPin,
        /** 연결 계좌 기관 식별자 */
        @NotNull Long institutionId,
        /** 연결 계좌번호 */
        @NotBlank @Pattern(regexp = "\\d{8,20}") String accountNumber) {}
