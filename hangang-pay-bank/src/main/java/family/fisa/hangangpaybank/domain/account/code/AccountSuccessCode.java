package family.fisa.hangangpaybank.domain.account.code;

import family.fisa.hangangpaybank.global.code.success.BaseSuccessCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@AllArgsConstructor
@Getter
public enum AccountSuccessCode implements BaseSuccessCode {
    BANK_ACCOUNT_CREATED(HttpStatus.OK, "BANK_ACCOUNT_CREATED", "계좌가 생성되었습니다."),
    BANK_ACCOUNT_DETAIL_OK(HttpStatus.OK, "BANK_ACCOUNT_DETAIL_OK", "계좌 정보 조회 성공");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
