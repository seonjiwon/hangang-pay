package family.fisa.hangangpay.client.bank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.client.bank.code.ExternalBankErrorCode;
import family.fisa.hangangpay.client.bank.dto.BankActResult;
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
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import family.fisa.hangangpay.global.response.ApiResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class BankClientImpl implements BankClient {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** Bank 서버의 에러 코드를 비즈니스 에러 코드로 매핑 */
    private static final Map<String, BaseErrorCode> BANK_ERROR_MAPPINGS =
            Map.of(
                    AccountErrorCode.BANK_ACCOUNT_NOT_FOUND.getCode(),
                    AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);

    private final RestClient bankRestClient;

    @Override
    public BankAccountResponse createBankAccount(BankAccountCreateRequest request) {
        // 1. 은행에 사용자 계좌 등록 요청
        ApiResponse<BankAccountResponse> response =
                callBank(
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
                callBank(
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
                callBank(
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
                callBank(
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
                callBank(
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
                callBank(
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

    /**
     * 환전 실행. error mapping/throw 대신 HTTP 상태로 결과를 분류 2xx=SUCCESS, 4xx=FAILURE(명시적 실패),
     * 5xx/타임아웃/IO=UNKNOWN
     */
    @Override
    public BankActResult exchange(ExchangeRequest request) {
        log.info(
                "bank 환전 호출. transactionUuid={}, amount={}",
                request.transactionUuid(),
                request.amount());

        try {
            ApiResponse<ExchangeResponse> response =
                    bankRestClient
                            .post()
                            .uri("/api/v1/transactions/exchange")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(request)
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            ExchangeResponse body = response.getResult();
            log.info(
                    "bank 환전 성공(SUCCESS). transactionUuid={}, bankTransactionId={}",
                    request.transactionUuid(),
                    body.bankTransactionId());

            return BankActResult.success(body);
        } catch (RestClientResponseException ex) {
            // 4xx = 은행이 명확히 거절한 실패
            if (ex.getStatusCode().is4xxClientError()) {
                log.warn(
                        "bank 환전 명시적 실패(FAILURE, 4xx). transactionUuid={}, status={}",
                        request.transactionUuid(),
                        ex.getStatusCode());
                return BankActResult.failure();
            }

            // 5xx = 서버 오류 -> 차감 여부 불확실
            log.error(
                    "bank 환전 불확실(UNKNOWN, 5xx). transactionUuid={}, status={}",
                    request.transactionUuid(),
                    ex.getStatusCode(),
                    ex);
            return BankActResult.unknown();
        } catch (ResourceAccessException ex) {
            // 타임아웃/커넥션 등 IO 오류 -> 응답을 못 받음 = 불확실
            log.error(
                    "bank 환전 불확실(UNKNOWN, 타임아웃/IO). transactionUuid={}",
                    request.transactionUuid(),
                    ex);
            return BankActResult.unknown();
        }
    }

    /**
     * 환전 상태 조회. 404 -> NOT_FOUND, 2xx body 의 status -> SUCCESS/FAILED/PENDING. 5xx/타임아웃은 그대로 전파
     * (reconcile이 다음 기회에 재시도)
     */
    @Override
    public BankExchangeStatus getStatus(String transactionUuid) {
        log.info("bank 환전 상태 조회. transactionUuid={}", transactionUuid);
        try {
            ApiResponse<BankTransactionStatusResponse> response =
                    bankRestClient
                            .get()
                            .uri("/api/v1/transactions/{transactionUuid}/status", transactionUuid)
                            .retrieve()
                            .body(new ParameterizedTypeReference<>() {});

            return BankExchangeStatus.from(response.getResult());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                log.info("bank 환전 상태: 거래 없음(NOT_FOUND). transactionUuid={}", transactionUuid);
                return BankExchangeStatus.notFound();
            }
            throw ex; // 5xx 등은 reconcile 재시도 대상
        }
    }

    @Override
    public PaymentResponse payment(PaymentRequest request) {
        // 1. 은행에 결제 요청 (지갑 → 지갑 transfer)
        ApiResponse<PaymentResponse> response =
                callBank(
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
                callBank(
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

    /** Bank가 의미 있는 에러 응답을 주면 그대로 전달한다. */
    private <T> ApiResponse<T> callBank(Supplier<ApiResponse<T>> request) {
        try {
            return request.get();
        } catch (RestClientResponseException ex) {
            throw mapBankError(ex).map(BusinessException::new).orElseThrow(() -> ex);
        }
    }

    /** Bank 서버의 에러를 서비스 서버의 에러 코드로 매핑 */
    private Optional<BaseErrorCode> mapBankError(RestClientResponseException ex) {
        try {
            BankErrorResponse response =
                    OBJECT_MAPPER.readValue(ex.getResponseBodyAsString(), BankErrorResponse.class);

            BaseErrorCode mapped = BANK_ERROR_MAPPINGS.get(response.code());
            if (mapped != null) {
                return Optional.of(mapped);
            }

            if (response.code() != null && response.message() != null) {
                return Optional.of(
                        new ExternalBankErrorCode(
                                HttpStatus.valueOf(ex.getStatusCode().value()),
                                response.code(),
                                response.message()));
            }

            return Optional.empty();
        } catch (JsonProcessingException parseException) {
            log.warn("Failed to parse bank error response. body={}", ex.getResponseBodyAsString());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankErrorResponse(String code, String message) {}
}
