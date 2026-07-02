package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentRateLimiter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPaymentRateLimiter implements PaymentRateLimiter {

    private static final String INTENT_KEY_PREFIX = "payment:rate:intent:";
    private static final String EXECUTE_KEY_PREFIX = "payment:rate:execute:";
    private static final String RECONCILE_KEY_PREFIX = "payment:rate:recovery:";
    private static final String BANK_OUTBOUND_KEY = "payment:rate:bank-outbound";

    private static final Duration TOKEN_BUCKET_TTL = Duration.ofMinutes(30);
    private static final Duration BANK_OUTBOUND_TTL = Duration.ofMinutes(1);
    private static final Duration RECONCILE_WINDOW = Duration.ofSeconds(60);

    private static final long INTENT_CAPACITY = 10L;
    private static final double INTENT_REFILL_RATE_PER_MS = 1.0d / 60_000;

    private static final Duration EXECUTE_WINDOW = Duration.ofMinutes(10);
    private static final long EXECUTE_LIMIT = 5L;

    private static final long BANK_OUTBOUND_CAPACITY = 50L;
    private static final double BANK_OUTBOUND_REFILL_RATE_PER_MS = 10.0d / 1_000;

    private static final long RECONCILE_LIMIT = 3L;

    private static final DefaultRedisScript<Long> TOKEN_BUCKET_SCRIPT =
            new DefaultRedisScript<>(
                    """
            local now_ms = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local refill_rate = tonumber(ARGV[3])
            local ttl_ms = tonumber(ARGV[4])

            local tokens = redis.call('hget', KEYS[1], 'tokens')
            local last_refill_ms = redis.call('hget', KEYS[1], 'last_refill_ms')

            if not tokens then
                tokens = capacity
            else
                tokens = tonumber(tokens)
            end

            if not last_refill_ms then
                last_refill_ms = now_ms
            else
                last_refill_ms = tonumber(last_refill_ms)
            end

            local elapsed_ms = math.max(0, now_ms - last_refill_ms)
            tokens = math.min(capacity, tokens + (elapsed_ms * refill_rate))

            if tokens >= 1 then
                tokens = tokens - 1
                redis.call('hset', KEYS[1], 'tokens', tokens, 'last_refill_ms', now_ms)
                redis.call('pexpire', KEYS[1], ttl_ms)
                return 1
            end

            redis.call('hset', KEYS[1], 'tokens', tokens, 'last_refill_ms', now_ms)
            redis.call('pexpire', KEYS[1], ttl_ms)
            return 0
            """,
                    Long.class);

    private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT =
            new DefaultRedisScript<>(
                    """
            local now_ms = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            local member = ARGV[4]
            local ttl_ms = tonumber(ARGV[5])

            redis.call('zremrangebyscore', KEYS[1], 0, now_ms - window_ms)

            local count = redis.call('zcard', KEYS[1])
            if count < limit then
                redis.call('zadd', KEYS[1], now_ms, member)
                redis.call('pexpire', KEYS[1], ttl_ms)
                return 1
            end

            redis.call('pexpire', KEYS[1], ttl_ms)
            return 0
            """,
                    Long.class);

    private final StringRedisTemplate redisTemplate;

    @Override
    public void checkIntentRateLimit(Long partyId, Long merchantPartyId) {
        checkTokenBucket(
                INTENT_KEY_PREFIX + partyId,
                INTENT_CAPACITY,
                INTENT_REFILL_RATE_PER_MS,
                TOKEN_BUCKET_TTL);
    }

    @Override
    public void checkExecutionRateLimit(
            Long partyId, Long merchantPartyId, String transactionUuid) {
        checkSlidingWindow(EXECUTE_KEY_PREFIX + partyId, EXECUTE_WINDOW, EXECUTE_LIMIT);
    }

    @Override
    public void checkReconcileRateLimit(Long partyId, String transactionUuid) {
        checkSlidingWindow(RECONCILE_KEY_PREFIX + partyId, RECONCILE_WINDOW, RECONCILE_LIMIT);
    }

    @Override
    public void checkBankOutboundRateLimit() {
        checkTokenBucket(
                BANK_OUTBOUND_KEY,
                BANK_OUTBOUND_CAPACITY,
                BANK_OUTBOUND_REFILL_RATE_PER_MS,
                BANK_OUTBOUND_TTL);
    }

    private void checkTokenBucket(String key, long capacity, double refillRatePerMs, Duration ttl) {
        Long allowed =
                redisTemplate.execute(
                        TOKEN_BUCKET_SCRIPT,
                        List.of(key),
                        String.valueOf(System.currentTimeMillis()),
                        String.valueOf(capacity),
                        String.valueOf(refillRatePerMs),
                        String.valueOf(ttl.toMillis()));

        if (!Long.valueOf(1L).equals(allowed)) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
        }
    }

    private void checkSlidingWindow(String key, Duration window, long limit) {
        long nowMs = System.currentTimeMillis();
        String member = nowMs + ":" + UUID.randomUUID();

        Long allowed =
                redisTemplate.execute(
                        SLIDING_WINDOW_SCRIPT,
                        List.of(key),
                        String.valueOf(nowMs),
                        String.valueOf(window.toMillis()),
                        String.valueOf(limit),
                        member,
                        String.valueOf(window.plusSeconds(1).toMillis()));

        if (!Long.valueOf(1L).equals(allowed)) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
        }
    }
}
