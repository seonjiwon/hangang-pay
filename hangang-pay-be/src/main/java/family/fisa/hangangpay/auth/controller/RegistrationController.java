package family.fisa.hangangpay.auth.controller;

import family.fisa.hangangpay.auth.code.AuthSuccessCode;
import family.fisa.hangangpay.auth.dto.request.UserRegisterRequest;
import family.fisa.hangangpay.auth.dto.response.UserRegisterResponse;
import family.fisa.hangangpay.auth.service.BusinessInfoQueryService;
import family.fisa.hangangpay.auth.service.MerchantRegistrationService;
import family.fisa.hangangpay.auth.service.UserRegistrationService;
import family.fisa.hangangpay.domain.merchant.dto.request.MerchantRegisterRequest;
import family.fisa.hangangpay.domain.merchant.dto.response.BusinessInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantRegisterResponse;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원가입", description = "소비자/가맹점 회원가입 API")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final UserRegistrationService userRegistrationService;
    private final MerchantRegistrationService merchantRegistrationService;
    private final BusinessInfoQueryService businessInfoQueryService;

    @Operation(summary = "소비자 회원가입 (REG-001)", description = "인증된 휴대폰과 계좌 정보로 소비자 회원가입을 완료한다.")
    @PostMapping("/users/register")
    public ResponseEntity<ApiResponse<UserRegisterResponse>> registerUser(
            @Valid @RequestBody UserRegisterRequest request, HttpSession session) {
        UserRegisterResponse response = userRegistrationService.register(request, session);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(AuthSuccessCode.USER_REGISTERED, response));
    }

    @Operation(summary = "사업자 정보 조회 (REG-002)", description = "사업자번호로 가맹점 사업자 정보를 조회한다.")
    @GetMapping("/merchants/business-info")
    public ResponseEntity<ApiResponse<BusinessInfoResponse>> getBusinessInfo(
            @RequestParam String businessNumber) {
        BusinessInfoResponse response = businessInfoQueryService.getBusinessInfo(businessNumber);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(summary = "가맹점 회원가입 (REG-003)", description = "인증된 계좌 정보와 사업자번호로 가맹점 회원가입을 완료한다.")
    @PostMapping("/merchants/register")
    public ResponseEntity<ApiResponse<MerchantRegisterResponse>> registerMerchant(
            @Valid @RequestBody MerchantRegisterRequest request, HttpSession session) {
        MerchantRegisterResponse response = merchantRegistrationService.register(request, session);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(AuthSuccessCode.MERCHANT_REGISTERED, response));
    }
}
