package family.fisa.hangangpay.domain.merchant.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record MerchantRegisterRequest(
        @NotBlank String businessNumber,
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank @Pattern(regexp = "\\d{6}") String paymentPin,
        @NotNull Long institutionId,
        @NotBlank @Pattern(regexp = "\\d{8,20}") String accountNumber,
        @NotBlank String phoneNumber,
        @Valid @NotNull TermsAgreed termsAgreed) {

    public record TermsAgreed(
            @AssertTrue boolean serviceTerms,
            @AssertTrue boolean privacyTerms,
            @AssertTrue boolean electronicFinanceTerms,
            @AssertTrue boolean localCurrencyTerms) {}
}
