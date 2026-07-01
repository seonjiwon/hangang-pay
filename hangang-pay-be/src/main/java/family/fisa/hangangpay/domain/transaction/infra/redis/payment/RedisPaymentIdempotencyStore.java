package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 같은 요청인지 판단하는 클래스 */
@Component
@RequiredArgsConstructor
public class RedisPaymentIdempotencyStore implements PaymentIdempotencyStore {
    private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);
    private static final String KEY_PREFIX = "payment:idempotency:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentIdempotencyDecision beginExecution(
            String transactionUuid, String requestHash, Long transactionId) {

        String key = key(transactionUuid);

        // 첫 실행 요청은 Redis에 PROCESSING 상태를 원자적으로 선점한다.
        // 같은 transactionUuid로 동시 요청 시 하나만 created=true가 된다.
        PaymentIdempotencyRecord newRecord =
                PaymentIdempotencyRecord.processing(transactionUuid, requestHash, transactionId);

        Boolean created =
                redisTemplate.opsForValue().setIfAbsent(key, serialize(newRecord), IDEMPOTENCY_TTL);

        if (Boolean.TRUE.equals(created)) {
            return PaymentIdempotencyDecision.newRequest();
        }

        PaymentIdempotencyRecord existing = readRecord(key);

        // transactionUuid는 같지만 서버 계산 requestHash가 다르면 같은 결제로 재사용하면 안 된다.
        if (!existing.requestHash().equals(requestHash)) {
            return PaymentIdempotencyDecision.conflict();
        }

        // 완료된 동일 요청은 Bank를 다시 호출하지 않고 저장된 응답 snapshot을 재사용한다. (UNKNOWN / SUCCESS)
        if (existing.responseSnapshot() != null) {
            return PaymentIdempotencyDecision.returnSnapshot(existing.responseSnapshot());
        }

        // snapshot은 없지만 status가 FAILED면, 복구가 실패로 확정한 거래다 → 재시도 거절함. (은행 요청 전, 죽은 PROCESSING)
        if (existing.status() == TransactionStatus.FAILED) {
            return PaymentIdempotencyDecision.alreadyFailed();
        }

        // snapshot이 없으면 기존 요청이 아직 Bank 호출 또는 후처리 중인 상태다.
        return PaymentIdempotencyDecision.processing();
    }

    @Override
    public void completeExecution(String transactionUuid, PaymentExecuteResponse responseSnapshot) {
        String key = key(transactionUuid);
        PaymentIdempotencyRecord existing = readRecord(key);

        // 성공 응답 snapshot을 저장해서 이후 동일 요청 재시도에 그대로 반환한다.
        PaymentIdempotencyRecord completed = existing.complete(responseSnapshot);

        redisTemplate.opsForValue().set(key, serialize(completed), IDEMPOTENCY_TTL);
    }

    @Override
    public void failExecution(String transactionUuid) {
        // 1. 기존 선점 record(PROCESSING, snapshot = null) 인 경우를 꺼낸다.
        String key = key(transactionUuid);
        PaymentIdempotencyRecord existing = readRecord(key);

        // 2. FAILED 마킹 + snapshot 제거한다.
        // 이후 같은 uuid 재요청은 beginExecution에서 ALREADY_FAILED
        redisTemplate.opsForValue().set(key, serialize(existing.fail()), IDEMPOTENCY_TTL);
    }

    private String key(String transactionUuid) {
        return KEY_PREFIX + transactionUuid;
    }

    private PaymentIdempotencyRecord readRecord(String key) {
        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_IDEMPOTENCY_RECORD_NOT_FOUND);
        }

        // Redis에 저장된 멱등성 record가 JSON으로 읽히지 않으면 서버 내부 상태 오류로 처리한다.
        try {
            return objectMapper.readValue(value, PaymentIdempotencyRecord.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_IDEMPOTENCY_RECORD_INVALID);
        }
    }

    private String serialize(PaymentIdempotencyRecord record) {
        // Redis 저장 전 멱등성 record를 JSON 문자열로 변환한다.
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            throw new BusinessException(TransactionErrorCode.PAYMENT_IDEMPOTENCY_RECORD_INVALID);
        }
    }
}
