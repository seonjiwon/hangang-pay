package family.fisa.hangangpay.client.bank.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * bank 호출 실패의 구조화된 표현. 재시도/종단 실패/불확실 분류에 필요한 원본 신호를 보존한다.
 *
 * <p>{@code status} : HTTP 응답을 받았을 때의 상태 코드. 응답 자체를 못 받으면(타임아웃/IO) {@code null}. {@code code} /
 * {@code message} : bank가 실은 {@code {code, message}} 에러 body. 파싱 실패 시 {@code null}.
 */
public record BankError(HttpStatusCode status, String code, String message) {

    /** 응답을 못 받은 IO/타임아웃 실패 (status 없음) */
    public static BankError noResponse(String message) {
        return new BankError(null, null, message);
    }

    /** HTTP 에러 응답 (code/message는 파싱 가능한 경우에만) */
    public static BankError of(HttpStatusCode status, String code, String message) {
        return new BankError(status, code, message);
    }

    /** HTTP 응답을 받았는지 여부. false = 네트워크 IO/타임아웃 */
    public boolean responseReceived() {
        return status != null;
    }

    public boolean is5xx() {
        return status != null && status.is5xxServerError();
    }

    public boolean isStatus(HttpStatus target) {
        return status != null && status.isSameCodeAs(target);
    }
}
