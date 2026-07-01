package family.fisa.hangangpay.auth.controller;

import family.fisa.hangangpay.auth.dto.request.LoginRequest;
import family.fisa.hangangpay.auth.dto.request.MerchantLoginRequest;
import family.fisa.hangangpay.auth.dto.response.LoginResponse;
import family.fisa.hangangpay.auth.service.AuthService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "사용자/가맹점 로그인 및 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "사용자 로그인", description = "휴대폰 번호와 비밀번호로 사용자 세션을 생성한다.")
    @PostMapping("/users/login")
    public ResponseEntity<ApiResponse<LoginResponse>> loginUser(
            @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(true);
        LoginResponse response = authService.loginUser(request, session);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(summary = "가맹점 로그인", description = "사업자번호와 비밀번호로 가맹점 세션을 생성한다.")
    @PostMapping("/merchants/login")
    public ResponseEntity<ApiResponse<LoginResponse>> loginMerchant(
            @RequestBody MerchantLoginRequest request, HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(true);
        LoginResponse response = authService.loginMerchant(request, session);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    @Operation(summary = "로그아웃", description = "현재 세션을 만료한다.")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<?>> logout(HttpServletRequest servletRequest) {
        authService.logout(servletRequest.getSession(false));
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK));
    }
}
