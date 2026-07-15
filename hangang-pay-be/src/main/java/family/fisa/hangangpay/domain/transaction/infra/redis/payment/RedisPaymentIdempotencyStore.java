package family.fisa.hangangpay.domain.transaction.infra.redis.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.code.TransactionErrorCode;
import family.fisa.hangangpay.domain.transaction.dto.user.response.PaymentExecuteResponse;
import family.fisa.hangangpay.domain.transaction.infra.redis.AbstractRedisIdempotencyStore;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.internal.payment.PaymentIdempotencyStore;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 같은 요청인지 판단하는 클래스 */
@Component  // [벤치마크] Full v1: 멱등 스냅샷을 Redis로 (v0/DB 복원 시 이쪽을 끈다)
public class RedisPaymentIdempotencyStore
        extends AbstractRedisIdempotencyStore<PaymentExecuteResponse, PaymentIdempotencyRecord>
        implements PaymentIdempotencyStore {

    public RedisPaymentIdempotencyStore(
            StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        super(redisTemplate, objectMapper);
    }

    @Override
    protected String keyPrefix() {
        return "payment:idempotency:";
    }

    @Override
    protected Class<PaymentIdempotencyRecord> recordType() {
        return PaymentIdempotencyRecord.class;
    }

    @Override
    protected BaseErrorCode notFoundError() {
        return TransactionErrorCode.PAYMENT_IDEMPOTENCY_RECORD_NOT_FOUND;
    }

    @Override
    protected BaseErrorCode invalidError() {
        return TransactionErrorCode.PAYMENT_IDEMPOTENCY_RECORD_INVALID;
    }

    @Override
    public IdempotencyDecision<PaymentExecuteResponse> beginExecution(
            IdempotencyKey idempotencyKey, Long transactionId) {
        return begin(
                idempotencyKey,
                PaymentIdempotencyRecord.processing(
                        idempotencyKey.idempotencyId(),
                        idempotencyKey.requestHash(),
                        transactionId));
    }

    @Override
    public void completeExecution(String transactionUuid, PaymentExecuteResponse responseSnapshot) {
        String key = key(transactionUuid);
        // 성공 응답 snapshot을 저장해서 이후 동일 요청 재시도에 그대로 반환한다.
        save(key, readRecord(key).complete(responseSnapshot));
    }

    @Override
    public void failExecution(String transactionUuid) {
        // FAILED 마킹 + snapshot 제거. 이후 같은 uuid 재요청은 beginExecution에서 ALREADY_FAILED.
        String key = key(transactionUuid);
        save(key, readRecord(key).fail());
    }
}
