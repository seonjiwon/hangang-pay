package family.fisa.hangangpay.domain.transaction.repository;

import family.fisa.hangangpay.domain.transaction.entity.Idempotency;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import family.fisa.hangangpay.domain.transaction.repository.jpa.IdempotencyJpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepositoryImpl implements IdempotencyRepository {

    private final IdempotencyJpaRepository jpaRepository;

    @Override
    public Optional<Idempotency> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey);
    }

    @Override
    public Idempotency save(Idempotency idempotency) {
        return jpaRepository.save(idempotency);
    }

    @Override
    public int updateResponse(
            String idempotencyKey, String responseJson, TransactionStatus status) {
        return jpaRepository.updateResponse(idempotencyKey, responseJson, status);
    }

    @Override
    public int updateStatus(String idempotencyKey, TransactionStatus status) {
        return jpaRepository.updateStatus(idempotencyKey, status);
    }

    @Override
    public int deleteExpiredBefore(LocalDateTime threshold) {
        return jpaRepository.deleteByTtlExpiryBefore(threshold);
    }
}
