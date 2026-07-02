package family.fisa.hangangpay.client.bank;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.client.bank.code.ExternalBankErrorCode;
import family.fisa.hangangpay.client.bank.exception.BankError;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/**
 * bank 에러 해석의 단일 권위. Spring 전송 예외를 구조화된 {@link BankException}으로 변환(파싱)하고, 재시도 여부/종단 코드/응답 코드를 한 곳에서
 * 결정한다.
 *
 * <p>분류 규칙: 응답 없음(IO/타임아웃) 또는 (코드 없는) 5xx·409 → 재시도(일시적). 재시도 코드({@link #RETRYABLE_BANK_CODES}) 를
 * 제외한 은행 코드가 있으면 → 종단 실패. 그 외 코드 없는 4xx → 종단 실패.
 */
@Slf4j
@Component
public class BankErrorInterpreter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** bank에서 재시도할 만한 일시적 비즈니스 코드 */
    private static final Set<String> RETRYABLE_BANK_CODES =
            Set.of("TRANSACTION_DUPLICATE_PROCESSING");

    /** 은행 에러 코드 → 서비스 공통 에러 코드 (조회/등록 경로) */
    private static final Map<String, BaseErrorCode> BANK_ERROR_MAPPINGS =
            Map.of(
                    AccountErrorCode.BANK_ACCOUNT_NOT_FOUND.getCode(),
                    AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);

    /** Spring 전송 예외를 구조화된 {@link BankException}으로 변환한다. */
    public BankException translate(RuntimeException ex) {
        // 1. HTTP 응답(4xx/5xx)을 받은 경우 → status + 파싱한 은행 코드 보존
        if (ex instanceof RestClientResponseException rcre) {
            BankErrorPayload payload = parseBody(rcre.getResponseBodyAsString());
            return new BankException(
                    BankError.of(rcre.getStatusCode(), payload.code(), payload.message()));
        }
        // 2. 네트워크 IO/타임아웃 → 응답 없음
        if (ex instanceof ResourceAccessException) {
            return new BankException(BankError.noResponse(ex.getMessage()));
        }
        // 3. 알 수 없는 전송 예외 → 응답 없음으로 취급(UNKNOWN 유도)
        return new BankException(BankError.noResponse(ex.getMessage()));
    }

    /** 재시도(일시적) 여부. */
    public boolean isRetryable(BankError error) {
        // 1. 응답 없음(IO/타임아웃) → 재시도
        if (!error.responseReceived()) {
            return true;
        }
        // 2. 은행 코드가 있으면 재시도 코드만 재시도
        if (error.code() != null) {
            return RETRYABLE_BANK_CODES.contains(error.code());
        }
        // 3. 코드를 못 읽으면 순수 5xx / 409만 재시도
        return error.is5xx() || error.isStatus(HttpStatus.CONFLICT);
    }

    /** 돈 흐름 종단 실패의 정규화 코드. bank 코드를 플로우별 failCodeMap으로 매핑, 없으면 fallback. */
    public BaseErrorCode resolveTerminalCode(
            BankError error, Map<String, BaseErrorCode> failCodeMap, BaseErrorCode fallback) {
        if (error.code() == null) {
            return fallback;
        }
        return failCodeMap.getOrDefault(error.code(), fallback);
    }

    /** 조회/등록 경로에서 GlobalExceptionHandler가 응답으로 변환할 코드. */
    public BaseErrorCode toResponseCode(BankError error) {
        // 1. 응답 없음 / 5xx → 은행 서버 오류
        if (!error.responseReceived() || error.is5xx()) {
            return GeneralErrorCode.BANK_SERVER_ERROR;
        }

        String code = error.code();
        // 2. 매핑된 도메인 코드 우선
        BaseErrorCode mapped = code == null ? null : BANK_ERROR_MAPPINGS.get(code);
        if (mapped != null) {
            return mapped;
        }

        // 3. code+message가 있으면 은행 코드를 그대로 노출(상태 보존)
        if (code != null && error.message() != null) {
            HttpStatus status = HttpStatus.resolve(error.status().value());
            if (status != null) {
                return new ExternalBankErrorCode(status, code, error.message());
            }
        }

        // 4. 그 외 4xx → 은행 호출 실패
        return GeneralErrorCode.BANK_CALL_FAILED;
    }

    /** 은행 에러 body({@code {code, message}})만 추출. 파싱 실패 시 (null, null). */
    private BankErrorPayload parseBody(String responseBody) {
        try {
            BankErrorPayload payload =
                    OBJECT_MAPPER.readValue(responseBody, BankErrorPayload.class);
            return payload == null ? new BankErrorPayload(null, null) : payload;
        } catch (Exception ignore) {
            return new BankErrorPayload(null, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record BankErrorPayload(String code, String message) {}
}
