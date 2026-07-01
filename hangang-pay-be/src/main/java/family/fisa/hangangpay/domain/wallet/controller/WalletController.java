package family.fisa.hangangpay.domain.wallet.controller;

import family.fisa.hangangpay.domain.wallet.code.WalletSuccessCode;
import family.fisa.hangangpay.domain.wallet.dto.response.WalletBalanceResponse;
import family.fisa.hangangpay.domain.wallet.service.WalletQueryService;
import family.fisa.hangangpay.global.response.ApiResponse;
import family.fisa.hangangpay.global.session.SessionAttributeNames;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.SessionAttribute;

/** 지갑 관련 HTTP 요청 처리 컨트롤러 */
@Tag(name = "지갑", description = "지갑 API")
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    /** 지갑 조회 서비스 */
    private final WalletQueryService walletQueryService;

    @Operation(summary = "잔액 조회 (WALLET-001)", description = "partyId 기준으로 지갑 잔액을 조회한다.")
    /** 세션 partyId 기준 지갑 잔액 조회 */
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(
            @SessionAttribute(SessionAttributeNames.PARTY_ID) Long partyId) {
        // 잔액 조회 후 응답 반환
        WalletBalanceResponse response = walletQueryService.getBalance(partyId);
        return ResponseEntity.ok(
                ApiResponse.onSuccess(WalletSuccessCode.WALLET_BALANCE_RETRIEVED, response));
    }
}
