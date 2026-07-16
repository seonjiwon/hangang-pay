package family.fisa.hangangpay.domain.transaction.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.entity.TransactionType;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class IntentCreationGuardTest {

    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    private final RequestHashDigest digest = new RequestHashDigest();
    private IntentCreationGuard guard;

    private static final Long PARTY_ID = 10L;
    private static final BigDecimal AMOUNT = new BigDecimal("50000");

    @BeforeEach
    void setUp() {
        guard = new IntentCreationGuard(redisTemplate, digest);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("첫 요청: SET NX 성공 -> 통과")
    void 첫_요청_통과() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        assertThatCode(() -> guard.check(TransactionType.EXCHANGE, PARTY_ID, AMOUNT))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("3초 내 같은 요청: 키 존재 -> INTENT_DUPLICATE_REQUEST 차단")
    void 중복_차단() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        assertThatThrownBy(() -> guard.check(TransactionType.EXCHANGE, PARTY_ID, AMOUNT))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(TransactionErrorCode.INTENT_DUPLICATE_REQUEST);
    }

    @Test
    @DisplayName("다른 amount -> 다른 키 (충돌 없이 통과)")
    void 다른_amount_다른_키() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        guard.check(TransactionType.EXCHANGE, PARTY_ID, new BigDecimal("1000"));
        guard.check(TransactionType.EXCHANGE, PARTY_ID, new BigDecimal("2000"));

        verify(valueOps, times(2)).setIfAbsent(keyCaptor.capture(), any(), any(Duration.class));
        assertThat(keyCaptor.getAllValues().get(0)).isNotEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    @DisplayName("Redis 예외 -> fail-open 통과 (부하 제어 장치, 가용성 우선)")
    void 레디스_예외_failopen() {
        when(valueOps.setIfAbsent(anyString(), any(), any(Duration.class)))
                .thenThrow(new RuntimeException("redis down"));

        assertThatCode(() -> guard.check(TransactionType.EXCHANGE, PARTY_ID, AMOUNT))
                .doesNotThrowAnyException();
    }
}
