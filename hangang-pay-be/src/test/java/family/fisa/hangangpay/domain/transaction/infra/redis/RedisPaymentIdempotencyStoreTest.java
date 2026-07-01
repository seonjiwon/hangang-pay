package family.fisa.hangangpay.domain.transaction.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.infra.redis.payment.PaymentIdempotencyRecord;
import family.fisa.hangangpay.domain.transaction.infra.redis.payment.RedisPaymentIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyDecisionType;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
class RedisPaymentIdempotencyStoreTest {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final Long TRANSACTION_ID = 123L;
    private static final String TRANSACTION_UUID = "11111111-1111-1111-1111-111111111111";
    private static final String REQUEST_HASH = "server-generated-request-hash";
    private static final String DIFFERENT_REQUEST_HASH = "different-request-hash";
    private static final String KEY = "payment:idempotency:" + TRANSACTION_UUID;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private RedisPaymentIdempotencyStore redisPaymentIdempotencyStore;

    @BeforeEach
    void setUp() {
        objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        redisPaymentIdempotencyStore =
                new RedisPaymentIdempotencyStore(redisTemplate, objectMapper);

        given(redisTemplate.opsForValue()).willReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis에 기존 record가 없으면 PROCESSING record를 생성하고 NEW_REQUEST를 반환한다")
    void beginExecution_noExistingRecordReturnsNewRequest() throws Exception {
        // setIfAbsent가 true면 이 요청이 transactionUuid의 첫 실행 요청이다.
        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(true);

        PaymentIdempotencyDecision decision =
                redisPaymentIdempotencyStore.beginExecution(
                        TRANSACTION_UUID, REQUEST_HASH, TRANSACTION_ID);

        assertThat(decision.type()).isEqualTo(PaymentIdempotencyDecisionType.NEW_REQUEST);
        assertThat(decision.responseSnapshot()).isNull();

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        // 첫 요청 선점 시 Redis에는 snapshot 없이 PROCESSING 상태만 저장한다.
        PaymentIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.PROCESSING);
        assertThat(saved.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.responseSnapshot()).isNull();

        verify(valueOperations, never()).get(KEY);
    }

    @Test
    @DisplayName("기존 record와 requestHash가 다르면 CONFLICT를 반환한다")
    void beginExecution_differentHashReturnsConflict() throws Exception {
        PaymentIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        PaymentIdempotencyDecision decision =
                redisPaymentIdempotencyStore.beginExecution(
                        TRANSACTION_UUID, DIFFERENT_REQUEST_HASH, TRANSACTION_ID);

        // 같은 transactionUuid라도 requestHash가 다르면 같은 결제 재시도가 아니다.
        assertThat(decision.type()).isEqualTo(PaymentIdempotencyDecisionType.CONFLICT);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("같은 requestHash이고 snapshot이 있으면 RETURN_SNAPSHOT을 반환한다")
    void beginExecution_sameHashWithSnapshotReturnsSnapshot() throws Exception {
        PaymentExecuteResponse snapshot = successSnapshot();
        PaymentIdempotencyRecord existing =
                record(TransactionStatus.SUCCESS, REQUEST_HASH, snapshot);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        PaymentIdempotencyDecision decision =
                redisPaymentIdempotencyStore.beginExecution(
                        TRANSACTION_UUID, REQUEST_HASH, TRANSACTION_ID);

        // 완료된 동일 요청은 Bank를 다시 호출하지 않도록 저장된 응답을 돌려준다.
        assertThat(decision.type()).isEqualTo(PaymentIdempotencyDecisionType.RETURN_SNAPSHOT);
        assertThat(decision.responseSnapshot()).isEqualTo(snapshot);
    }

    @Test
    @DisplayName("같은 requestHash이지만 snapshot이 없으면 PROCESSING을 반환한다")
    void beginExecution_sameHashWithoutSnapshotReturnsProcessing() throws Exception {
        PaymentIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);

        given(valueOperations.setIfAbsent(eq(KEY), anyString(), eq(IDEMPOTENCY_TTL)))
                .willReturn(false);
        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        PaymentIdempotencyDecision decision =
                redisPaymentIdempotencyStore.beginExecution(
                        TRANSACTION_UUID, REQUEST_HASH, TRANSACTION_ID);

        // snapshot이 없다는 것은 기존 요청이 아직 Bank 호출 또는 후처리 중이라는 뜻이다.
        assertThat(decision.type()).isEqualTo(PaymentIdempotencyDecisionType.PROCESSING);
        assertThat(decision.responseSnapshot()).isNull();
    }

    @Test
    @DisplayName("completeExecution은 기존 record에 성공 snapshot을 저장한다")
    void completeExecution_storesSnapshot() throws Exception {
        PaymentIdempotencyRecord existing =
                record(TransactionStatus.PROCESSING, REQUEST_HASH, null);
        PaymentExecuteResponse snapshot = successSnapshot();

        given(valueOperations.get(KEY)).willReturn(writeRecord(existing));

        redisPaymentIdempotencyStore.completeExecution(TRANSACTION_UUID, snapshot);

        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(KEY), valueCaptor.capture(), eq(IDEMPOTENCY_TTL));

        // 완료 후에는 동일 요청 재시도를 위해 최종 응답 snapshot을 함께 저장한다.
        PaymentIdempotencyRecord saved = readRecord(valueCaptor.getValue());
        assertThat(saved.transactionUuid()).isEqualTo(TRANSACTION_UUID);
        assertThat(saved.requestHash()).isEqualTo(REQUEST_HASH);
        assertThat(saved.status()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(saved.transactionId()).isEqualTo(TRANSACTION_ID);
        assertThat(saved.responseSnapshot()).isEqualTo(snapshot);
    }

    private PaymentIdempotencyRecord record(
            TransactionStatus status, String requestHash, PaymentExecuteResponse responseSnapshot) {
        return new PaymentIdempotencyRecord(
                TRANSACTION_UUID, requestHash, status, TRANSACTION_ID, responseSnapshot);
    }

    private PaymentExecuteResponse successSnapshot() {
        return new PaymentExecuteResponse(
                TRANSACTION_UUID,
                TransactionStatus.SUCCESS,
                "APV-2026-00000123",
                new BigDecimal("10000"),
                "성수 한강카페",
                LocalDateTime.of(2026, 5, 25, 10, 0));
    }

    private String writeRecord(PaymentIdempotencyRecord record) throws Exception {
        return objectMapper.writeValueAsString(record);
    }

    private PaymentIdempotencyRecord readRecord(String value) throws Exception {
        return objectMapper.readValue(value, PaymentIdempotencyRecord.class);
    }
}
