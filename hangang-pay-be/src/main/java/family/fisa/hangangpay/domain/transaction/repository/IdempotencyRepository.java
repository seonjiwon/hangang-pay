package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Idempotency;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;
import java.util.Optional;

/** 멱등성 레코드 저장소 포트. */
public interface IdempotencyRepository {

    Optional<Idempotency> findByIdempotencyKey(String idempotencyKey);

    Idempotency save(Idempotency idempotency);

    /** 완료 응답 + 최종 상태 반영. 반환: 영향 행 수 */
    int updateResponse(String idempotencyKey, String responseJson, TransactionStatus status);

    /** 상태만 변경 */
    int updateStatus(String idempotencyKey, TransactionStatus status);

    /** 만료분 정리 */
    int deleteExpiredBefore(LocalDateTime threshold);
}
