package family.fisa.hangangpay.client.bank;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpay.client.bank.code.ExternalBankErrorCode;
import family.fisa.hangangpay.client.bank.exception.BankError;
import family.fisa.hangangpay.client.bank.exception.BankException;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.code.error.GeneralErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

@DisplayName("BankErrorInterpreter 분류 규칙")
class BankErrorInterpreterTest {

    private final BankErrorInterpreter interpreter = new BankErrorInterpreter();

    private static final Map<String, BaseErrorCode> FAIL_CODE_MAP =
            Map.of(
                    "TRANSACTION_INSUFFICIENT_BALANCE",
                    TransactionErrorCode.PAYMENT_INSUFFICIENT_BALANCE);
    private static final BaseErrorCode FALLBACK = TransactionErrorCode.PAYMENT_FAILED;

    private BankError httpError(int status, String code, String message) {
        return BankError.of(HttpStatusCode.valueOf(status), code, message);
    }

    private RestClientResponseException rcre(int status, String body) {
        byte[] bytes = body == null ? null : body.getBytes(StandardCharsets.UTF_8);
        return new RestClientResponseException(
                "err", status, "err", null, bytes, StandardCharsets.UTF_8);
    }

    // ── translate ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("translate: Spring 예외 → BankException")
    class Translate {

        @Test
        @DisplayName("HTTP 에러(JSON body) → status + code + message 보존")
        void httpError_parsed() {
            BankException ex =
                    interpreter.translate(
                            rcre(
                                    400,
                                    "{\"code\":\"TRANSACTION_INSUFFICIENT_BALANCE\",\"message\":\"잔액부족\"}"));

            BankError error = ex.getError();
            assertThat(error.responseReceived()).isTrue();
            assertThat(error.isStatus(HttpStatus.BAD_REQUEST)).isTrue();
            assertThat(error.code()).isEqualTo("TRANSACTION_INSUFFICIENT_BALANCE");
            assertThat(error.message()).isEqualTo("잔액부족");
        }

        @Test
        @DisplayName("HTTP 에러(파싱 불가 body) → code/message null, status 보존")
        void httpError_unparseable() {
            BankException ex = interpreter.translate(rcre(500, "not-json"));

            BankError error = ex.getError();
            assertThat(error.responseReceived()).isTrue();
            assertThat(error.is5xx()).isTrue();
            assertThat(error.code()).isNull();
        }

        @Test
        @DisplayName("네트워크 IO/타임아웃 → 응답 없음")
        void resourceAccess_noResponse() {
            BankException ex = interpreter.translate(new ResourceAccessException("timeout"));

            BankError error = ex.getError();
            assertThat(error.responseReceived()).isFalse();
            assertThat(error.status()).isNull();
        }
    }

    // ── isRetryable ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isRetryable: 재시도 여부")
    class Retryable {

        @Test
        @DisplayName("응답 없음(IO) → 재시도")
        void noResponse_retryable() {
            assertThat(interpreter.isRetryable(BankError.noResponse("timeout"))).isTrue();
        }

        @Test
        @DisplayName("5xx + code 없음 → 재시도")
        void serverError_noCode_retryable() {
            assertThat(interpreter.isRetryable(httpError(500, null, null))).isTrue();
        }

        @Test
        @DisplayName("409 DUPLICATE_PROCESSING → 재시도")
        void duplicateProcessing_retryable() {
            assertThat(
                            interpreter.isRetryable(
                                    httpError(409, "TRANSACTION_DUPLICATE_PROCESSING", "dup")))
                    .isTrue();
        }

        @Test
        @DisplayName("4xx + 종단 비즈니스 code → 재시도 안 함")
        void businessReject_notRetryable() {
            assertThat(
                            interpreter.isRetryable(
                                    httpError(400, "TRANSACTION_INSUFFICIENT_BALANCE", "잔액부족")))
                    .isFalse();
        }

        @Test
        @DisplayName("4xx + code 없음 → 재시도 안 함")
        void clientError_noCode_notRetryable() {
            assertThat(interpreter.isRetryable(httpError(400, null, null))).isFalse();
        }

        @Test
        @DisplayName("5xx + 종단 code(EXCHANGE_CONTRACT_FAILED) → 재시도 안 함(종단)")
        void serverError_withTerminalCode_notRetryable() {
            assertThat(interpreter.isRetryable(httpError(502, "EXCHANGE_CONTRACT_FAILED", "실패")))
                    .isFalse();
        }
    }

    // ── resolveTerminalCode ─────────────────────────────────────────────────

    @Nested
    @DisplayName("resolveTerminalCode: 종단 코드 매핑")
    class ResolveTerminalCode {

        @Test
        @DisplayName("매핑된 bank code → 도메인 코드")
        void mapped() {
            BaseErrorCode code =
                    interpreter.resolveTerminalCode(
                            httpError(400, "TRANSACTION_INSUFFICIENT_BALANCE", "잔액부족"),
                            FAIL_CODE_MAP,
                            FALLBACK);
            assertThat(code).isEqualTo(TransactionErrorCode.PAYMENT_INSUFFICIENT_BALANCE);
        }

        @Test
        @DisplayName("매핑 없는 bank code → fallback")
        void unmapped_fallback() {
            BaseErrorCode code =
                    interpreter.resolveTerminalCode(
                            httpError(400, "SOME_OTHER_CODE", "x"), FAIL_CODE_MAP, FALLBACK);
            assertThat(code).isEqualTo(FALLBACK);
        }

        @Test
        @DisplayName("code 없음 → fallback")
        void noCode_fallback() {
            BaseErrorCode code =
                    interpreter.resolveTerminalCode(
                            httpError(400, null, null), FAIL_CODE_MAP, FALLBACK);
            assertThat(code).isEqualTo(FALLBACK);
        }
    }

    // ── toResponseCode ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("toResponseCode: 조회/등록 경로 응답 코드")
    class ToResponseCode {

        @Test
        @DisplayName("5xx → BANK_SERVER_ERROR")
        void serverError() {
            assertThat(interpreter.toResponseCode(httpError(500, "X", "y")))
                    .isEqualTo(GeneralErrorCode.BANK_SERVER_ERROR);
        }

        @Test
        @DisplayName("응답 없음 → BANK_SERVER_ERROR")
        void noResponse() {
            assertThat(interpreter.toResponseCode(BankError.noResponse("timeout")))
                    .isEqualTo(GeneralErrorCode.BANK_SERVER_ERROR);
        }

        @Test
        @DisplayName("매핑된 code → 도메인 코드(BANK_ACCOUNT_NOT_FOUND)")
        void mappedCode() {
            BankError error =
                    httpError(404, AccountErrorCode.BANK_ACCOUNT_NOT_FOUND.getCode(), "없음");
            assertThat(interpreter.toResponseCode(error))
                    .isEqualTo(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);
        }

        @Test
        @DisplayName("매핑 없는 4xx code+message → ExternalBankErrorCode(상태 보존)")
        void unmapped4xx_external() {
            BaseErrorCode code =
                    interpreter.toResponseCode(httpError(400, "SOME_BANK_CODE", "bank msg"));

            assertThat(code).isInstanceOf(ExternalBankErrorCode.class);
            assertThat(code.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(code.getCode()).isEqualTo("SOME_BANK_CODE");
            assertThat(code.getMessage()).isEqualTo("bank msg");
        }

        @Test
        @DisplayName("4xx + code 없음 → BANK_CALL_FAILED")
        void clientError_noCode() {
            assertThat(interpreter.toResponseCode(httpError(400, null, null)))
                    .isEqualTo(GeneralErrorCode.BANK_CALL_FAILED);
        }
    }
}
