package family.fisa.hangangpay.domain.transaction.internal.cancel;

import static org.assertj.core.api.Assertions.assertThat;

import family.fisa.hangangpay.domain.transaction.internal.RequestHashDigest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CancelRequestHashGenerator - 취소 의도 해시")
class CancelRequestHashGeneratorTest {

    private final CancelRequestHashGenerator generator =
            new CancelRequestHashGenerator(new RequestHashDigest());

    @Test
    @DisplayName("같은 원본결제+가맹점은 같은 해시를 만든다")
    void 같은_입력_같은_해시() {
        assertThat(generator.generate("origin-uuid", 100L))
                .isEqualTo(generator.generate("origin-uuid", 100L));
    }

    @Test
    @DisplayName("같은 원본결제라도 취소 요청 가맹점이 다르면 해시가 달라져 충돌로 감지한다")
    void 다른_가맹점_다른_해시() {
        // 1. 같은 결제에 대해 다른 가맹점이 취소를 시도하는 상황
        String merchant100 = generator.generate("origin-uuid", 100L);
        String merchant101 = generator.generate("origin-uuid", 101L);

        // 2. 해시가 달라야 IdempotencyStore에서 CONFLICT로 걸린다
        assertThat(merchant100).isNotEqualTo(merchant101);
    }
}
