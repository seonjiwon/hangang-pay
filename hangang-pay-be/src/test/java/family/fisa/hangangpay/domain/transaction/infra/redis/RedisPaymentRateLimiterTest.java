package family.fisa.hangangpay.domain.transaction.infra.redis;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.infra.redis.payment.RedisPaymentRateLimiter;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisPaymentRateLimiterTest {

    private static final Long PARTY_ID = 1L;
    private static final Long MERCHANT_PARTY_ID = 2L;
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";

    @Mock private StringRedisTemplate redisTemplate;

    private RedisPaymentRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RedisPaymentRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("intent rate limit이 허용되면 예외가 발생하지 않는다")
    void checkIntentRateLimit_allowedDoesNotThrow() {
        given(
                        redisTemplate.execute(
                                any(RedisScript.class),
                                eq(List.of("payment:rate:intent:" + PARTY_ID)),
                                anyString(),
                                eq("10"),
                                anyString(),
                                anyString()))
                .willReturn(1L);

        assertThatCode(() -> rateLimiter.checkIntentRateLimit(PARTY_ID, MERCHANT_PARTY_ID))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("intent rate limit이 거절되면 PAYMENT_RATE_LIMIT_EXCEEDED 예외가 발생한다")
    void checkIntentRateLimit_deniedThrowsRateLimitExceeded() {
        given(
                        redisTemplate.execute(
                                any(RedisScript.class),
                                eq(List.of("payment:rate:intent:" + PARTY_ID)),
                                anyString(),
                                eq("10"),
                                anyString(),
                                anyString()))
                .willReturn(0L);

        assertThatThrownBy(() -> rateLimiter.checkIntentRateLimit(PARTY_ID, MERCHANT_PARTY_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("execution rate limit이 거절되면 PAYMENT_RATE_LIMIT_EXCEEDED 예외가 발생한다")
    void checkExecutionRateLimit_deniedThrowsRateLimitExceeded() {
        given(
                        redisTemplate.execute(
                                any(RedisScript.class),
                                eq(List.of("payment:rate:execute:" + PARTY_ID)),
                                anyString(), // now_ms
                                anyString(), // window_ms
                                eq("5"), // limit
                                anyString(), // member
                                anyString())) // ttl_ms
                .willReturn(0L);

        assertThatThrownBy(
                        () ->
                                rateLimiter.checkExecutionRateLimit(
                                        PARTY_ID, MERCHANT_PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("recovery rate limit이 거절되면 PAYMENT_RATE_LIMIT_EXCEEDED 예외가 발생한다")
    void checkRecoveryRateLimit_deniedThrowsRateLimitExceeded() {
        given(
                        redisTemplate.execute(
                                any(RedisScript.class),
                                eq(List.of("payment:rate:recovery:" + PARTY_ID)),
                                anyString(),
                                anyString(),
                                eq("3"),
                                anyString(),
                                anyString()))
                .willReturn(0L);

        assertThatThrownBy(() -> rateLimiter.checkReconcileRateLimit(PARTY_ID, TRANSACTION_UUID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("bank outbound rate limit이 거절되면 PAYMENT_RATE_LIMIT_EXCEEDED 예외가 발생한다")
    void checkBankOutboundRateLimit_deniedThrowsRateLimitExceeded() {
        given(
                        redisTemplate.execute(
                                any(RedisScript.class),
                                eq(List.of("payment:rate:bank-outbound")),
                                anyString(),
                                eq("50"),
                                anyString(),
                                anyString()))
                .willReturn(0L);

        assertThatThrownBy(() -> rateLimiter.checkBankOutboundRateLimit())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue(
                        "code", TransactionErrorCode.PAYMENT_RATE_LIMIT_EXCEEDED);
    }
}
