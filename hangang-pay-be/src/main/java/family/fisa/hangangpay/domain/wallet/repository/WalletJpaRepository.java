package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByParty_Id(Long partyId);

    Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId);

    Wallet save(Wallet wallet);

    /** 비관적 락 - 동일 파티의 동시 요청을 wallet 행 락으로 직렬화 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.party.id = :partyId")
    Optional<Wallet> findByParty_IdForUpdate(@Param("partyId") Long partyId);

    /** 비관적 락(NOWAIT) - 락 소유자가 있으면 대기 없이 즉시 실패 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "0"))
    @Query("SELECT w FROM Wallet w WHERE w.party.id = :partyId")
    Optional<Wallet> findByParty_IdForUpdateNoWait(@Param("partyId") Long partyId);
}
