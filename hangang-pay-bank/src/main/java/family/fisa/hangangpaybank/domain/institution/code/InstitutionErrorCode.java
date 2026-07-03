package family.fisa.hangangpaybank.domain.institution.code;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InstitutionErrorCode implements BaseErrorCode {
    INSTITUTION_NOT_FOUND(HttpStatus.NOT_FOUND, "INSTITUTION_NOT_FOUND", "기관을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
