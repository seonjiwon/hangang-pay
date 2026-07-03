package family.fisa.hangangpaybank.domain.institution.code;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InstitutionErrorCode implements BaseErrorCode {
    INSTITUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "기관을 찾을 수 없습니다."),
    INSTITUTION_INVALID_WALLET_KEY(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "INSTITUTION_INVALID_WALLET_KEY",
            "기관 지갑 키 정보가 올바르지 않습니다."),
    BANK_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_ACCOUNT_NOT_FOUND", "계좌를 찾을 수 없습니다."),
    BANK_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
