package family.fisa.hangangpay.auth.controller;

import family.fisa.hangangpay.auth.dto.request.AccountSendRequest;
import family.fisa.hangangpay.auth.dto.request.AccountVerifyRequest;
import family.fisa.hangangpay.auth.dto.request.SmsSendRequest;
import family.fisa.hangangpay.auth.dto.request.SmsVerifyRequest;
import family.fisa.hangangpay.auth.dto.response.VerificationCodeResponse;
import family.fisa.hangangpay.auth.service.VerificationService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SMS 및 계좌 1원 인증 컨트롤러 */
@Tag(name = "인증", description = "SMS 및 계좌 1원 인증")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    /** SMS 인증 코드 발송 엔드포인트 */
    @Operation(summary = "SMS 인증 코드 발송 (AUTH-001)", description = "휴대폰 번호로 6자리 인증 코드를 발송한다.")
    @PostMapping("/sms/send")
    public ResponseEntity<ApiResponse<VerificationCodeResponse>> sendSms(
            @Valid @RequestBody SmsSendRequest request, HttpSession session) {
        String code = verificationService.sendSms(request.phoneNumber(), session);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        GeneralSuccessCode.COMMON_OK, new VerificationCodeResponse(code)));
    }

    /** SMS 인증 코드 검증 엔드포인트 */
    @Operation(summary = "SMS 인증 코드 검증 (AUTH-002)", description = "발급된 코드와 입력값을 비교해 인증을 완료한다.")
    @PostMapping("/sms/verify")
    public ResponseEntity<ApiResponse<?>> verifySms(
            @Valid @RequestBody SmsVerifyRequest request, HttpSession session) {
        verificationService.verifySms(request.phoneNumber(), request.code(), session);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK));
    }

    /** 계좌 1원 인증 코드 발송 엔드포인트 */
    @Operation(summary = "계좌 1원 인증 코드 발송 (AUTH-003)", description = "계좌번호로 6자리 인증 코드를 발송한다.")
    @PostMapping("/account/send")
    public ResponseEntity<ApiResponse<VerificationCodeResponse>> sendAccountVerification(
            @Valid @RequestBody AccountSendRequest request, HttpSession session) {
        String code =
                verificationService.sendAccountVerification(
                        request.institutionId(), request.accountNumber(), session);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        GeneralSuccessCode.COMMON_OK, new VerificationCodeResponse(code)));
    }

    /** 계좌 1원 인증 코드 검증 엔드포인트 */
    @Operation(summary = "계좌 1원 인증 코드 검증 (AUTH-004)", description = "발급된 코드와 입력값을 비교해 인증을 완료한다.")
    @PostMapping("/account/verify")
    public ResponseEntity<ApiResponse<?>> verifyAccount(
            @Valid @RequestBody AccountVerifyRequest request, HttpSession session) {
        verificationService.verifyAccount(
                request.institutionId(), request.accountNumber(), request.code(), session);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK));
    }
}
