package family.fisa.hangangpay.domain.transaction.internal;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
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

    // 요청 해시(SHA-256 조작검사) 계산 비용 계측용 Timer.
    // 모든 flow(charge/payment/cancel/exchange/IntentCreationGuard)의 해시가 이 지점을 통과하므로 전 구간 커버.
    // Prometheus: request_hash_seconds_{count,sum,bucket}.
    //   rate(request_hash_seconds_sum) = 초당 해시에 쓴 시간(코어 점유 근사) → "해시가 CPU를 얼마나 먹나"의 답.
    private final Timer hashTimer;

    public RequestHashDigest(MeterRegistry meterRegistry) {
        this.hashTimer =
                Timer.builder("request.hash")
                        .description("SHA-256 request-hash (tamper check) computation time")
                        .publishPercentileHistogram()
                        // 마이크로초 수준을 재므로 히스토그램 범위를 좁혀 p99 해상도를 확보한다.
                        .minimumExpectedValue(Duration.ofNanos(1_000))
                        .maximumExpectedValue(Duration.ofMillis(50))
                        .register(meterRegistry);
    }

    /** 전달받은 필드들을 정규화 직렬화한 뒤 SHA-256 HEX 문자열로 반환 */
    public String digest(Object... parts) {
        // 해시 계산 전 구간을 Timer로 감싸 CPU 비용을 계측한다.
        return hashTimer.record(
                () -> {
                    // 1. 각 필드를 문자열로 변환하고 구분자를 짓는다.
                    String raw =
                            Arrays.stream(parts)
                                    .map(part -> part == null ? NULL_TOKEN : part.toString())
                                    .collect(Collectors.joining(DELIMITER));
                    // 2. SHA-256 다이제스트
                    byte[] hash = sha256(raw);

                    // 3. 바이트 배열을 소문자 hex 문자열로 변환한다. (Redis 저장/문자열 비교에 쓰기 좋은 형태)
                    return HexFormat.of().formatHex(hash);
                });
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
