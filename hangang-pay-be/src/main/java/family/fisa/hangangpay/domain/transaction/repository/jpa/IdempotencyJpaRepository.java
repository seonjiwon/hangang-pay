package family.fisa.hangangpay.domain.transaction.repository.jpa;

import family.fisa.hangangpay.domain.transaction.entity.Idempotency;
import family.fisa.hangangpay.domain.transaction.entity.TransactionStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IdempotencyJpaRepository extends JpaRepository<Idempotency, Long> {

    Optional<Idempotency> findByIdempotencyKey(String idempotencyKey);

    @Modifying
    @Query(
            "UPDATE Idempotency i SET i.responseJson = :responseJson, i.status = :status "
                    + "WHERE i.idempotencyKey = :idempotencyKey")
    int updateResponse(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("responseJson") String responseJson,
            @Param("status") TransactionStatus status);

    @Modifying
    @Query("UPDATE Idempotency i SET i.status = :status WHERE i.idempotencyKey = :idempotencyKey")
    int updateStatus(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("status") TransactionStatus status);

    @Modifying
    @Query("DELETE FROM Idempotency i WHERE i.ttlExpiry < :threshold")
    int deleteByTtlExpiryBefore(@Param("threshold") LocalDateTime threshold);
}
