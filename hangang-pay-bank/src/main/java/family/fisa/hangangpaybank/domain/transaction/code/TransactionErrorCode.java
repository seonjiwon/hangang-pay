package family.fisa.hangangpaybank.domain.transaction.code;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TransactionErrorCode implements BaseErrorCode {
    TRANSACTION_INSUFFICIENT_BALANCE(
            HttpStatus.BAD_REQUEST, "TRANSACTION_INSUFFICIENT_BALANCE", "잔액이 부족합니다."),
    TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "해당 거래를 찾을 수 없습니다."),
    TRANSACTION_DUPLICATE_PROCESSING(
            HttpStatus.CONFLICT, "TRANSACTION_DUPLICATE_PROCESSING", "동일한 거래가 이미 처리 중입니다."),
    TRANSACTION_ALREADY_FAILED(
            HttpStatus.UNPROCESSABLE_ENTITY, "TRANSACTION_ALREADY_FAILED", "이미 실패한 거래입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
