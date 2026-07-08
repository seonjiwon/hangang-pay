package family.fisa.hangangpay.domain.transaction.internal.payment;

import family.fisa.hangangpay.domain.transaction.entity.Transaction;
import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentRequestHashGenerator {

    private final RequestHashDigest requestHashDigest;

    /**
     * 결제 실행 멱등 해시. 결제는 intent 단계에서 금액/송수신자가 고정되므로, 요청 payload가 아니라 이미 저장된 PENDING 거래의 의도 필드로 해시를
     * 만든다.
     */
    public String generatePaymentExecuteHash(Transaction transaction) {
        // 금액은 scale 차이로 해시가 갈리지 않도록 값 기준을 정규화한다.
        String normalizedAmount = transaction.getAmount().stripTrailingZeros().toPlainString();

        // 누가 -> 누구에게 -> 얼마 를 결제 의도로 규정한다.
        return requestHashDigest.digest(
                transaction.getFromParty().getId(),
                transaction.getToParty().getId(),
                normalizedAmount);
    }

    /**
     * 결제 intent dedup용 fingerprint. execute 해시({@link #generatePaymentExecuteHash})와 같은 재료
     * (송신자·수신자·금액)를, 거래 저장 전 request 파라미터로 미리 계산해 Redis 선점 키로 쓴다.
     */
    public String generateIntentExecutionHash(Long fromPartyId, Long toPartyId, BigDecimal amount) {
        String normalizedAmount = amount.stripTrailingZeros().toPlainString();

        return requestHashDigest.digest(fromPartyId, toPartyId, normalizedAmount);
    }
}
