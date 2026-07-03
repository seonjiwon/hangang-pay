package family.fisa.hangangpaybank.domain.wallet.controller;

import family.fisa.hangangpaybank.domain.wallet.code.WalletSuccessCode;
import family.fisa.hangangpaybank.domain.wallet.dto.request.CreateBankWalletRequest;
import family.fisa.hangangpaybank.domain.wallet.dto.response.BankWalletResponse;
import family.fisa.hangangpaybank.domain.wallet.service.BankWalletCommandService;
import family.fisa.hangangpaybank.domain.wallet.service.BankWalletQueryService;
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
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "BankWallet", description = "은행 원장 지갑 API (Custodial)")
@RestController
@RequestMapping("/api/v1/bank-wallets")
@RequiredArgsConstructor
public class BankWalletController {

    private final BankWalletCommandService bankWalletCommandService;
    private final BankWalletQueryService bankWalletQueryService;

    @Operation(summary = "은행 원장 지갑 생성", description = "bank가 EC keypair를 생성하고 암호화된 개인키와 함께 저장한다.")
    @PostMapping
    public ResponseEntity<ApiResponse<BankWalletResponse>> create(
            @RequestBody CreateBankWalletRequest request) {
        // 1. bank_wallet 생성 (Custodial)
        BankWalletResponse response = bankWalletCommandService.create(request);

        // 2. 성공 응답 반환
        return ResponseEntity.status(WalletSuccessCode.BANK_WALLET_CREATED.getStatus())
                .body(ApiResponse.onSuccess(WalletSuccessCode.BANK_WALLET_CREATED, response));
    }

    @Operation(summary = "은행 원장 지갑 단건 조회", description = "지갑 주소로 원장 지갑을 조회한다.")
    @GetMapping("/address/{walletAddress}")
    public ResponseEntity<ApiResponse<BankWalletResponse>> get(@PathVariable String walletAddress) {
        // 1. bank_wallet 조회
        BankWalletResponse response = bankWalletQueryService.getByWalletAddress(walletAddress);

        // 2. 성공 응답 반환
        return ResponseEntity.status(WalletSuccessCode.BANK_WALLET_DETAIL_OK.getStatus())
                .body(
                        ApiResponse.onSuccess(
                                WalletSuccessCode.BANK_WALLET_DETAIL_OK, response));
    }
}
