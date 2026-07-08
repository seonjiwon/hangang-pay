package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIntentDedupStore;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPaymentIntentDedupStore implements PaymentIntentDedupStore {

    private static final String KEY_PREFIX = "payment:intent:dedup:"; // 결제 dedup 키 접두어
    private static final Duration DEDUP_TTL = Duration.ofSeconds(30); // 선점 유지 창(30초)

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> reserve(String fingerprint, String newTransactionUuid) {
        // 1. fingerprint로 dedup 키를 만든다.
        String key = KEY_PREFIX + fingerprint;

        // 2. SET NX + 30초 TTL: 키가 없을 때만 내 uuid로 선점한다.
        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(key, newTransactionUuid, DEDUP_TTL);

        // 3. 내가 처음 선점했으면 empty -> 신규 intent 진행
        if (Boolean.TRUE.equals(created)) {
            return Optional.empty();
        }

        // 4. 이미 선점돼 있으면(30초 내 중복 요청) 먼저 들어온 uuid를 돌려준다 = dedup 히트
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }
}
