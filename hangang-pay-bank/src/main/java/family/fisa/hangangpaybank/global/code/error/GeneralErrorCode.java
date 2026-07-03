package family.fisa.hangangpaybank.global.code.error;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum GeneralErrorCode implements BaseErrorCode {
    COMMON_INTERNAL_SERVER_ERROR(
            HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INTERNAL_SERVER_ERROR", "서버 내부 오류가 발생했습니다."),
    COMMON_INVALID_WALLET_KEY(
            HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_INVALID_WALLET_KEY", "지갑 키 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
