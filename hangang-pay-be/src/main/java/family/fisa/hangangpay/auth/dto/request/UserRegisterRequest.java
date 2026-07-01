package family.fisa.hangangpay.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

/** 소비자 회원가입 요청 DTO */
public record UserRegisterRequest(
        /** 사용자 실명 */
        @NotBlank String name,
        /** 생년월일 */
        @NotNull LocalDate birthDate,
        /** 휴대폰 번호 */
        @NotBlank String phoneNumber,
        /** 로그인 비밀번호 */
        @NotBlank String password,
        /** 결제 PIN */
        @NotBlank @Pattern(regexp = "\\d{6}") String paymentPin,
        /** 연결 계좌 기관 식별자 */
        @NotNull Long institutionId,
        /** 연결 계좌번호 */
        @NotBlank @Pattern(regexp = "\\d{8,20}") String accountNumber,
        /** 필수 약관 동의 여부 */
        @Valid @NotNull TermsAgreed termsAgreed) {

    /** 회원가입 필수 약관 동의 정보 */
    public record TermsAgreed(
            /** 서비스 이용약관 */
            @AssertTrue boolean serviceTerms,
            /** 개인정보 처리방침 */
            @AssertTrue boolean privacyTerms,
            /** 전자금융거래 약관 */
            @AssertTrue boolean electronicFinanceTerms,
            /** 지역화폐 이용약관 */
            @AssertTrue boolean localCurrencyTerms) {}
}
