package family.fisa.hangangpay.domain.transaction.internal.charge;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ChargeRequestHashGenerator - 충전 의도 해시")
class ChargeRequestHashGeneratorTest {

    private final ChargeRequestHashGenerator generator =
            new ChargeRequestHashGenerator(new RequestHashDigest(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));

    @Test
    @DisplayName("같은 충전 의도는 같은 해시를 만든다")
    void 같은_의도_같은_해시() {
        String first = generator.generate("uuid-1", 10L, 20L, new BigDecimal("50000"));
        String second = generator.generate("uuid-1", 10L, 20L, new BigDecimal("50000"));

        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("금액 scale 차이(50000 vs 50000.00)는 같은 해시로 정규화된다")
    void 금액_scale_무관() {
        // 1. 값은 같지만 scale 이 다른 금액
        String noScale = generator.generate("uuid-1", 10L, 20L, new BigDecimal("50000"));
        String withScale = generator.generate("uuid-1", 10L, 20L, new BigDecimal("50000.00"));

        // 2. stripTrailingZeros 정규화로 같은 해시여야 정상 재시도가 거짓 충돌이 안 난다
        assertThat(noScale).isEqualTo(withScale);
    }

    @Test
    @DisplayName("출금 계좌가 다르면 해시가 달라진다")
    void 다른_계좌_다른_해시() {
        String account20 = generator.generate("uuid-1", 10L, 20L, new BigDecimal("50000"));
        String account21 = generator.generate("uuid-1", 10L, 21L, new BigDecimal("50000"));

        assertThat(account20).isNotEqualTo(account21);
    }
}
