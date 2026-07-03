package family.fisa.hangangpaybank.domain.wallet.code;

import family.fisa.hangangpaybank.global.code.error.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WalletErrorCode implements BaseErrorCode {
    BANK_WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "BANK_WALLET_NOT_FOUND", "지갑을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
