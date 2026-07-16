package family.fisa.hangangpay.domain.transaction.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequestHashDigest - 공유 해시 메커니즘")
class RequestHashDigestTest {

    private final RequestHashDigest digest = new RequestHashDigest();

    @Test
    @DisplayName("같은 입력은 항상 같은 해시를 만든다 (결정성)")
    void 같은_입력_같은_해시() {
        // 1. 동일한 인자로 두 번 호출
        String first = digest.digest("a", 1L, "b");
        String second = digest.digest("a", 1L, "b");

        // 2. 결정적이어야 멱등 비교에 쓸 수 있다
        assertThat(first).isEqualTo(second);
    }

    @Test
    @DisplayName("필드가 하나라도 다르면 해시가 달라진다")
    void 다른_필드_다른_해시() {
        assertThat(digest.digest("a", 1L)).isNotEqualTo(digest.digest("a", 2L));
    }

    @Test
    @DisplayName("구분자가 값 경계를 고정해 (1,23)과 (12,3)을 다른 해시로 만든다")
    void 구분자_경계_충돌_방지() {
        // 1. 구분자가 없으면 둘 다 "123"으로 뭉개져 같은 해시가 된다
        assertThat(digest.digest("1", "23")).isNotEqualTo(digest.digest("12", "3"));
    }

    @Test
    @DisplayName("null 필드는 예외 없이 처리되고 빈 문자열과 구분된다")
    void null_안전() {
        // 1. null 이 들어와도 예외가 나지 않는다
        String withNull = digest.digest(null, "a");

        // 2. null 토큰과 빈 문자열은 서로 다른 해시여야 한다 (누락/공백 구분)
        assertThat(withNull).isNotEqualTo(digest.digest("", "a"));
    }

    @Test
    @DisplayName("출력은 SHA-256 hex 64자다")
    void SHA256_hex_길이() {
        assertThat(digest.digest("x")).hasSize(64).matches("[0-9a-f]+");
    }
}
