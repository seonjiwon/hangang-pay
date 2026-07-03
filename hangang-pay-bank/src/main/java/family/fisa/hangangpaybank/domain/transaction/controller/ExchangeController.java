package family.fisa.hangangpaybank.domain.transaction.controller;

import family.fisa.hangangpaybank.domain.transaction.code.TransactionSuccessCode;
import family.fisa.hangangpaybank.domain.transaction.dto.response.ExchangeStatusResponse;
import family.fisa.hangangpaybank.domain.transaction.service.exchange.ExchangeQueryService;
import family.fisa.hangangpaybank.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Exchange", description = "환전 API")
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
public class ExchangeController {

    private final ExchangeQueryService exchangeQueryService;

    @Operation(
            summary = "환전 상태 조회",
            description =
                    "BE가 발행한 transactionUuid로 bank의 환전 결과 두 ledger(account_ledger, blockchain_ledger)를 확인한다. 둘 다 있으면 SUCCESS, 하나라도 없으면 404.")
    @GetMapping("/{transactionUuid}/status")
    public ResponseEntity<ApiResponse<ExchangeStatusResponse>> getExchangeStatus(
            @PathVariable String transactionUuid) {
        // 1. 두 ledger 모두 있는지 확인하고 status 응답 생성
        ExchangeStatusResponse response = exchangeQueryService.getStatus(transactionUuid);

        // 2. 성공 응답 반환
        return ResponseEntity.ok(
                ApiResponse.onSuccess(TransactionSuccessCode.TRANSACTION_STATUS_OK, response));
    }
}
