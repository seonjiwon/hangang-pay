package family.fisa.hangangpay.auth.code;

import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "전화번호 또는 비밀번호가 올바르지 않습니다"),
    DUPLICATE_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "DUPLICATE_PHONE_NUMBER", "이미 가입된 휴대폰 번호입니다."),
    DUPLICATE_BUSINESS_NUMBER(
            HttpStatus.BAD_REQUEST, "DUPLICATE_BUSINESS_NUMBER", "이미 가입된 사업자번호입니다."),
    DUPLICATE_USERNAME(HttpStatus.BAD_REQUEST, "DUPLICATE_USERNAME", "이미 사용 중인 아이디입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
