package family.fisa.hangangpay.client.bank.dto;

import family.fisa.hangangpay.client.bank.dto.response.BankTransactionStatusResponse;

/** bank 환전 상태 조회(GET) 결과 */
public record BankExchangeStatus(Status status, Long bankTransactionId) {
    public enum Status {
        SUCCESS,
        FAILED,
        PENDING,
        NOT_FOUND
    }

    /** bank에 거래 없음 */
    public static BankExchangeStatus notFound() {
        return new BankExchangeStatus(Status.NOT_FOUND, null);
    }

    /** bank 응답(status 포함)을 4-상태로 매핑. SUCCESS/FAILED 외에는 PENDING으로 수렴 */
    public static BankExchangeStatus from(BankTransactionStatusResponse body) {
        Status mapped =
                switch (body.status()) {
                    case SUCCESS -> Status.SUCCESS;
                    case FAILED -> Status.FAILED;
                    default -> Status.PENDING;
                };
        return new BankExchangeStatus(mapped, body.bankTransactionId());
    }
}
