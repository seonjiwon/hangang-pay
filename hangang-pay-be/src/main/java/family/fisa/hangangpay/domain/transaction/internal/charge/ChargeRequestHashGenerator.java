package family.fisa.hangangpay.domain.transaction.internal.charge;

import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChargeRequestHashGenerator {

    // 1. 공유 컴포넌트에 위임
    private final RequestHashDigest requestHashDigest;

    /** 충전 의도를 규정하는 필드(transactionUuid, partyId, accountId, amount) 기준 해시 생성 */
    public String generate(
            String transactionUuid, Long partyId, Long accountId, BigDecimal amount) {

        // 2. 금액은 scale 차이로 해시가 갈리지 않도록 값 기준으로 정규화한다. (1.0 과 1.00 을 같은 해시로)
        String normalizedAmount = amount.stripTrailingZeros().toPlainString();

        // 3. 충전 의도 필드를 공유 digest에 넘긴다. 구분자/null 직렬화 규칙은 digest가 책임진다.
        return requestHashDigest.digest(transactionUuid, partyId, accountId, normalizedAmount);
    }
}