package family.fisa.hangangpay.client.bank.exception;

import lombok.Getter;

/**
 * bank 호출 실패를 나타내는 유일한 예외. 구조화된 {@link BankError}를 담아 상위 계층(executor / GlobalExceptionHandler)이
 * HTTP status와 은행 error code를 직접 보고 분류할 수 있게 한다.
 */
@Getter
public class BankException extends RuntimeException {

    private final transient BankError error;

    public BankException(BankError error) {
        super(buildMessage(error));
        this.error = error;
    }

    private static String buildMessage(BankError error) {
        String status = error.status() == null ? "NO_RESPONSE" : error.status().toString();
        return "Bank call failed. status=" + status + ", code=" + error.code();
    }
}
