package family.fisa.hangangpay.client.bank.dto;

import family.fisa.hangangpay.client.bank.dto.response.ExchangeResponse;

/**
 * bank exchange(POST) 호출 결과 분류 SUCCESS : 2xx 응답. body 포함 FAILURE : 4xx 등 명시적 실패 (검증 거절). 재시도 무의미
 * UNKNOWN : 타임아웃 / IO / 5xx. 차감 여부 모름 -> FAILED 가 아니다
 */
public record BankActResult(Type type, ExchangeResponse body) {

    public enum Type {
        SUCCESS,
        FAILURE,
        UNKNOWN
    }

    /** 2XX 성공 */
    public static BankActResult success(ExchangeResponse body) {
        return new BankActResult(Type.SUCCESS, body);
    }

    /** 명시적 실패 - body 없음 */
    public static BankActResult failure() {
        return new BankActResult(Type.FAILURE, null);
    }

    /** 응답 불확실 - body 없음 */
    public static BankActResult unknown() {
        return new BankActResult(Type.UNKNOWN, null);
    }
}
