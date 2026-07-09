package family.fisa.hangangpay.domain.transaction.infra.db;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import family.fisa.hangangpay.domain.transaction.entity.Idempotency;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyDecision;
import family.fisa.hangangpay.domain.transaction.internal.IdempotencyKey;
import family.fisa.hangangpay.domain.transaction.repository.IdempotencyRepository;
import family.fisa.hangangpay.global.code.error.BaseErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 멱등성 스토어 공통 로직. 키 생성, 선점 판정, 완료 응답 직렬화를 담는다.
 *
 * <p>플로우별 차이는 훅으로 노출한다: 키 prefix, 응답 타입, 에러코드, FAILED 상태 재요청 처리 여부.
 *
 * @param <S> 플로우별 실행 응답 snapshot 타입
 */
public abstract class AbstractDbIdempotencyStore<S> {

    protected static final Duration IDEMPOTENCY_TTL = Duration.ofDays(7);

    private final IdempotencyRepository idempotencyRepository;
    private final ObjectMapper objectMapper;

    protected AbstractDbIdempotencyStore(
            IdempotencyRepository idempotencyRepository, ObjectMapper objectMapper) {
        this.idempotencyRepository = idempotencyRepository;
        this.objectMapper = objectMapper;
    }

    /** 멱등성 키 prefix. 예: "payment:idempotency:" */
    protected abstract String keyPrefix();

    /** 응답 snapshot 역직렬화 타입 */
    protected abstract Class<S> responseType();

    /** 직렬화/역직렬화 실패 에러코드 */
    protected abstract BaseErrorCode invalidError();

    /** FAILED 상태 기존 요청을 재요청 시 ALREADY_FAILED로 거절할지 여부. */
    protected boolean honorFailedState() {
        return true;
    }

    protected final String key(String idempotencyId) {
        return keyPrefix() + idempotencyId;
    }

    /**
     * 첫 요청을 선점(레코드 생성)하고, 이미 존재하면 기존 상태로 판정한다.
     *
     * <p>없음 -> NEW_REQUEST / requestHash 불일치 -> CONFLICT / snapshot 있음 -> RETURN_SNAPSHOT / (honor 시)
     * FAILED -> ALREADY_FAILED / 그 외 -> PROCESSING
     */
    protected final IdempotencyDecision<S> begin(IdempotencyKey idempotencyKey, Long transactionId) {

        // 1. 이 실행의 멱등 키로 기존 레코드를 찾는다.
        String storeKey = key(idempotencyKey.idempotencyId());
        Optional<Idempotency> found = idempotencyRepository.findByIdempotencyKey(storeKey);

        // 2. 없으면 이번이 첫 요청 -> PROCESSING 레코드를 저장해 선점한다.
        if (found.isEmpty()) {
            idempotencyRepository.save(
                    Idempotency.builder()
                            .idempotencyKey(storeKey)
                            .transactionUuid(idempotencyKey.idempotencyId())
                            .requestHash(idempotencyKey.requestHash())
                            .transactionId(transactionId)
                            .status(TransactionStatus.PROCESSING)
                            .ttlExpiry(LocalDateTime.now().plus(IDEMPOTENCY_TTL))
                            .build());
            return IdempotencyDecision.newRequest();
        }

        Idempotency existing = found.get();

        // 3. 같은 키인데 요청 해시가 다르면 = 내용이 다른 요청 -> 충돌.
        if (!Objects.equals(existing.getRequestHash(), idempotencyKey.requestHash())) {
            return IdempotencyDecision.conflict();
        }

        // 4. 저장된 완료 응답이 있으면 그대로 재사용한다(snapshot).
        S snapshot = deserialize(existing.getResponseJson());
        if (snapshot != null) {
            return IdempotencyDecision.returnSnapshot(snapshot);
        }

        // 5. 응답은 없는데 FAILED로 끝난 요청이면 재시도를 거절한다.
        if (honorFailedState() && existing.getStatus() == TransactionStatus.FAILED) {
            return IdempotencyDecision.alreadyFailed();
        }

        // 6. 그 외 = 아직 처리 중.
        return IdempotencyDecision.processing();
    }

    /** 완료 응답 + 최종 상태 저장 */
    protected final void complete(
            String transactionUuid, S responseSnapshot, TransactionStatus status) {
        idempotencyRepository.updateResponse(
                key(transactionUuid), serialize(responseSnapshot), status);
    }

    /** 상태만 변경 */
    protected final void changeStatus(String transactionUuid, TransactionStatus status) {
        idempotencyRepository.updateStatus(key(transactionUuid), status);
    }

    private String serialize(S value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(invalidError());
        }
    }

    private S deserialize(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, responseType());
        } catch (JsonProcessingException e) {
            throw new BusinessException(invalidError());
        }
    }
}
