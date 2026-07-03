package family.fisa.hangangpaybank.domain.account.controller;

import family.fisa.hangangpaybank.domain.account.code.AccountSuccessCode;
import family.fisa.hangangpaybank.domain.account.dto.request.CreateBankAccountRequest;
import family.fisa.hangangpaybank.domain.account.dto.response.BankAccountResponse;
import family.fisa.hangangpaybank.domain.account.service.BankAccountCommandService;
import family.fisa.hangangpaybank.domain.account.service.BankAccountQueryService;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BankAccount", description = "은행 원장 계좌 API")
@RestController
@RequestMapping("/api/v1/bank-accounts")
@RequiredArgsConstructor
public class BankAccountController {

    private final BankAccountCommandService bankAccountCommandService;
    private final BankAccountQueryService bankAccountQueryService;

    @Operation(summary = "은행 원장 계좌 생성", description = "기관 ID와 계좌번호로 원장 계좌를 등록한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<BankAccountResponse>> create(
            @RequestBody CreateBankAccountRequest request) {
        // 1. bank_account 생성
        BankAccountResponse response = bankAccountCommandService.create(request);

        // 2. 성공 응답 반환
        return ResponseEntity.status(AccountSuccessCode.BANK_ACCOUNT_CREATED.getStatus())
                .body(ApiResponse.onSuccess(AccountSuccessCode.BANK_ACCOUNT_CREATED, response));
    }

    @Operation(summary = "은행 원장 계좌 단건 조회", description = "계좌번호 + 기관 ID로 원장 계좌를 조회한다.")
    @GetMapping("/{accountNumber}")
    public ResponseEntity<ApiResponse<BankAccountResponse>> get(
            @PathVariable String accountNumber, @RequestParam("institutionId") Long institutionId) {
        // 1. bank_account 조회
        BankAccountResponse response =
                bankAccountQueryService.getByAccountNumberAndInstitutionId(
                        accountNumber, institutionId);

        // 2. 성공 응답 반환
        return ResponseEntity.status(AccountSuccessCode.BANK_ACCOUNT_DETAIL_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                AccountSuccessCode.BANK_ACCOUNT_DETAIL_OK, response));
    }
}
