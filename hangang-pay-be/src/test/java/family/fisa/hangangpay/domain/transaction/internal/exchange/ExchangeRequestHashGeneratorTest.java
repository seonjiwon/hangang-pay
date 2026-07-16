package family.fisa.hangangpay.domain.transaction.internal.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ExchangeRequestHashGenerator - 환전 의도 해시")
class ExchangeRequestHashGeneratorTest {

    private final ExchangeRequestHashGenerator generator =
            new ExchangeRequestHashGenerator(new RequestHashDigest());

    @Test
    @DisplayName("같은 요청자+uuid는 같은 해시를 만든다")
    void 같은_입력_같은_해시() {
        assertThat(generator.generate(10L, "uuid-1")).isEqualTo(generator.generate(10L, "uuid-1"));
    }

    @Test
    @DisplayName("같은 uuid라도 요청자(partyId)가 다르면 해시가 달라져 cross-party 재사용을 충돌로 감지한다")
    void 다른_요청자_다른_해시() {
        // 1. 같은 환전 uuid를 다른 사용자가 실행 시도하는 상황
        String party10 = generator.generate(10L, "uuid-1");
        String party11 = generator.generate(11L, "uuid-1");

        // 2. 해시가 달라야 IdempotencyStore에서 CONFLICT로 걸린다
        assertThat(party10).isNotEqualTo(party11);
    }
}
