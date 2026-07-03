package family.fisa.hangangpaybank.domain.account.code;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements BaseErrorCode {
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
