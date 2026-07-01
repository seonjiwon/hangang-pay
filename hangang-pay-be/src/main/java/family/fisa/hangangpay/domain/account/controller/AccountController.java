package family.fisa.hangangpay.domain.account.controller;

import family.fisa.hangangpay.domain.account.dto.request.AccountCreateRequest;
import family.fisa.hangangpay.domain.account.dto.response.AccountListResponse;
import family.fisa.hangangpay.domain.account.dto.response.AccountResponse;
import family.fisa.hangangpay.domain.account.dto.response.PrimaryAccountResponse;
import family.fisa.hangangpay.domain.account.service.AccountCommandService;
import family.fisa.hangangpay.domain.account.service.AccountQueryService;
import family.fisa.hangangpay.global.code.success.GeneralSuccessCode;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/** 계좌 관련 HTTP 요청 처리 컨트롤러 */
@Tag(name = "계좌", description = "계좌 관리 API")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    /** 계좌 조회 서비스 */
    private final AccountQueryService accountQueryService;

    /** 계좌 쓰기 서비스 */
    private final AccountCommandService accountCommandService;

    /** ACCOUNT-001 등록 계좌 목록 조회 엔드포인트 */
    @Operation(summary = "등록 계좌 목록 조회 (ACCOUNT-001)", description = "현재 로그인한 사용자의 등록된 계좌 목록을 조회한다.")
    @GetMapping
    public ResponseEntity<ApiResponse<AccountListResponse>> getAccounts(
            @SessionAttribute(name = SessionAttributeNames.PARTY_ID, required = false)
                    Long partyId) {

        // 계좌 목록 조회 후 응답 반환
        AccountListResponse response = accountQueryService.getAccounts(partyId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }

    /** ACCOUNT-002 계좌 추가 엔드포인트 */
    @Operation(
            summary = "계좌 등록 (ACCOUNT-002)",
            description = "은행 원장 확인 및 예금주 검증 후 계좌를 등록한다. 최대 3개까지 등록 가능하다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @SessionAttribute(name = SessionAttributeNames.PARTY_ID, required = false) Long partyId,
            @Valid @RequestBody AccountCreateRequest request) {

        // 계좌 추가 후 201 응답 반환
        AccountResponse response = accountCommandService.createAccount(partyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_CREATED, response));
    }

    /** ACCOUNT-003 계좌 삭제 엔드포인트 */
    @Operation(
            summary = "계좌 삭제 (ACCOUNT-003)",
            description = "본인 계좌를 삭제한다. 주거래 계좌와 마지막 계좌는 삭제할 수 없다.")
    @DeleteMapping("/{accountId}")
    public ResponseEntity<ApiResponse<?>> deleteAccount(
            @SessionAttribute(name = SessionAttributeNames.PARTY_ID, required = false) Long partyId,
            @PathVariable Long accountId) {

        // 계좌 삭제 후 200 응답 반환
        accountCommandService.deleteAccount(partyId, accountId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK));
    }

    /** ACCOUNT-004 주거래 계좌 변경 엔드포인트 */
    @Operation(
            summary = "주거래 계좌 변경 (ACCOUNT-004)",
            description = "지정한 계좌를 주거래 계좌로 변경한다. 기존 주거래 계좌는 일반 계좌로 전환된다.")
    @PatchMapping("/{accountId}/primary")
    public ResponseEntity<ApiResponse<PrimaryAccountResponse>> updatePrimaryAccount(
            @SessionAttribute(name = SessionAttributeNames.PARTY_ID, required = false) Long partyId,
            @PathVariable Long accountId) {

        // 주거래 계좌 변경 후 200 응답 반환
        PrimaryAccountResponse response =
                accountCommandService.updatePrimaryAccount(partyId, accountId);
        return ResponseEntity.ok(ApiResponse.onSuccess(GeneralSuccessCode.COMMON_OK, response));
    }
}
