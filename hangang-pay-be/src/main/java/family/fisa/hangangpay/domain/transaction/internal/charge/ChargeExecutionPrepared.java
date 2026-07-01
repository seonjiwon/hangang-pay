package family.fisa.hangangpay.domain.transaction.internal.charge;

import family.fisa.hangangpay.client.bank.dto.request.ChargeRequest;
import java.math.BigDecimal;

/** 은행 충전 호출에 필요한 실행 준비 데이터 */
public record ChargeExecutionPrepared(
        String transactionUuid, // 거래 식별자
        String requestHash, // 충돌 감지용 요청 해시
        Long institutionId, // 금융기관 ID
        String accountNumber, // 출금 계좌번호
        String walletAddress, // 입금 지갑 주소
        BigDecimal finalAmount, // 계좌 차감 금액 (실 결제 금액)
        BigDecimal amount) { // 지갑 mint 금액 (충전가)

    /** BankClient 요청 DTO로 변환 */
    public ChargeRequest toBankChargeRequest() {
        return new ChargeRequest(
                transactionUuid, institutionId, accountNumber, walletAddress, finalAmount, amount);
    }
}
