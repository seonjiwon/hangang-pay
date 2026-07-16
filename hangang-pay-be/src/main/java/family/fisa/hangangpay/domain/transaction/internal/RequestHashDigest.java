package family.fisa.hangangpay.domain.transaction.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RequestHashDigest {
    // 필드 구분자. 값 경계를 고정 (1, 23 / 12, 3 을 같은 해시로 보지 않도록 방지)
    private static final String DELIMITER = ":";

    // null 필드를 위한 고정 토큰. (빈 문자열로 두면 누락과 구분이 안됨)
    private static final String NULL_TOKEN = "\u0000";

    /** 전달받은 필드들을 정규화 직렬화한 뒤 SHA-256 HEX 문자열로 반환 */
    public String digest(Object... parts) {
        // 1. 각 필드를 문자열로 변환하고 구분자를 짓는다.
        String raw =
                Arrays.stream(parts)
                        .map(part -> part == null ? NULL_TOKEN : part.toString())
                        .collect(Collectors.joining(DELIMITER));
        // 2. SHA-256 다이제스트
        byte[] hash = sha256(raw);

        // 3. 바이트 배열을 소문자 hex 문자열로 변환한다. (Redis 저장/문자열 비교에 쓰기 좋은 형태)
        return HexFormat.of().formatHex(hash);
    }

    private byte[] sha256(String raw) {
        try {
            // 2-1. SHA-256은 JDK 표준이라 항상 존재한다. 없으면 런타임 환경이 깨진 것이므로 실패시킨다.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(raw.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
