package family.fisa.hangangpay.client.bank;

import family.fisa.hangangpay.client.bank.dto.BankExchangeStatus;
import family.fisa.hangangpay.client.bank.dto.request.BankAccountCreateRequest;
import family.fisa.hangangpay.client.bank.dto.request.BankWalletCreateRequest;
import family.fisa.hangangpay.client.bank.dto.request.CancelRequest;
import family.fisa.hangangpay.client.bank.dto.request.ChargeRequest;
import family.fisa.hangangpay.client.bank.dto.request.ExchangeRequest;
import family.fisa.hangangpay.client.bank.dto.request.PaymentRequest;
import family.fisa.hangangpay.client.bank.dto.response.BankAccountResponse;
import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;
import family.fisa.hangangpay.client.bank.dto.response.BankWalletResponse;
import family.fisa.hangangpay.client.bank.dto.response.CancelResponse;
import family.fisa.hangangpay.client.bank.dto.response.ChargeResponse;
import family.fisa.hangangpay.client.bank.dto.response.ExchangeResponse;
import family.fisa.hangangpay.client.bank.dto.response.PaymentResponse;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.global.response.ApiResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * bank 연동의 얇은 전송 계층. HTTP 호출만 담당하고, 실패는 {@link BankErrorInterpreter}가 만든 {@link BankException} 으로
 * 던진다(HTTP status·은행 error code 신호를 죽이지 않는다). 재시도/종단/불확실 분류는 상위 계층이 담당한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BankClientImpl implements BankClient {

    private final RestClient bankRestClient;
    private final BankErrorInterpreter bankErrorInterpreter;

    @Override
    public BankAccountResponse createBankAccount(BankAccountCreateRequest request) {
        // 1. 은행에 사용자 계좌 등록 요청
        ApiResponse<BankAccountResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/bank-accounts")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankAccountResponse getBankAccount(Long institutionId, String accountNumber) {
        // 1. 은행에서 계좌 정보를 조회 (path variable + query parameter)
        ApiResponse<BankAccountResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri(
                                                uriBuilder ->
                                                        uriBuilder
                                                                .path(
                                                                        "/api/v1/bank-accounts/{accountNumber}")
                                                                .queryParam(
                                                                        "institutionId",
                                                                        institutionId)
                                                                .build(accountNumber))
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankWalletResponse createBankWallet(BankWalletCreateRequest request) {
        // 1. 은행에 사용자 지갑 발급 요청 (Custodial, bank가 keypair 생성)
        ApiResponse<BankWalletResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/bank-wallets")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankWalletResponse getBankWalletByAddress(String address) {
        // 1. 은행에서 지갑 정보를 조회
        ApiResponse<BankWalletResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri("/api/v1/bank-wallets/address/{address}", address)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public BankTransactionStatusResponse getTransactionStatus(String transactionUuid) {
        // 1. Bank 결제 상태 조회 (GET /api/v1/transactions/{uuid}/status)
        ApiResponse<BankTransactionStatusResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .get()
                                        .uri(
                                                "/api/v1/transactions/{transactionUuid}/payment/status",
                                                transactionUuid)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public ChargeResponse charge(ChargeRequest request) {
        // 1. 은행에 충전 요청 (계좌 → 토큰 mint)
        ApiResponse<ChargeResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/charge")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public ExchangeResponse exchange(ExchangeRequest request) {
        log.info(
                "bank 환전 호출. transactionUuid={}, amount={}",
                request.transactionUuid(),
                request.amount());

        ApiResponse<ExchangeResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/exchange")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        ExchangeResponse body = response.getResult();
        log.info(
                "bank 환전 응답 수신. transactionUuid={}, bankTransactionId={}",
                request.transactionUuid(),
                body.bankTransactionId());
        return body;
    }

    /**
     * 환전 상태 조회. 404 → NOT_FOUND, 2xx body의 status → SUCCESS/FAILED/PENDING. 5xx/타임아웃은 그대로 전파
     * (reconcile이 다음 기회에 재시도)
     */
    @Override
    public BankExchangeStatus getStatus(String transactionUuid) {
        log.info("bank 환전 상태 조회. transactionUuid={}", transactionUuid);
        try {
            ApiResponse<BankTransactionStatusResponse> response =
                    execute(
                            () ->
                                    bankRestClient
                                            .get()
                                            .uri(
                                                    "/api/v1/transactions/{transactionUuid}/status",
                                                    transactionUuid)
                                            .retrieve()
                                            .body(new ParameterizedTypeReference<>() {}));

            return BankExchangeStatus.from(response.getResult());
        } catch (BankException e) {
            if (e.getError().isStatus(HttpStatus.NOT_FOUND)) {
                log.info("bank 환전 상태: 거래 없음(NOT_FOUND). transactionUuid={}", transactionUuid);
                return BankExchangeStatus.notFound();
            }
            throw e; // 5xx 등은 reconcile 재시도 대상
        }
    }

    @Override
    public PaymentResponse payment(PaymentRequest request) {
        // 1. 은행에 결제 요청 (지갑 → 지갑 transfer)
        ApiResponse<PaymentResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/payment")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public CancelResponse cancel(CancelRequest request) {
        // 1. 은행에 결제 취소 요청 (역방향 transfer)
        ApiResponse<CancelResponse> response =
                execute(
                        () ->
                                bankRestClient
                                        .post()
                                        .uri("/api/v1/transactions/cancel")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .body(request)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<>() {}));

        // 2. 응답에서 결과 추출
        return response.getResult();
    }

    @Override
    public void localMint(Long institutionId, String walletAddress, BigDecimal amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("institutionId", institutionId);
        body.put("walletAddress", walletAddress);
        body.put("amount", amount);
        bankRestClient
                .post()
                .uri("/api/v1/internal/local/mint")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    /** bank 호출을 실행하고, 전송 예외는 구조화된 {@link BankException}으로 변환해 던진다. */
    private <T> T execute(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException | ResourceAccessException ex) {
            throw bankErrorInterpreter.translate(ex);
        }
    }
}
