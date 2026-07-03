package family.fisa.hangangpaybank.domain.account.repository.jpa;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountJpaRepository extends JpaRepository<BankAccount, Long> {

    Optional<BankAccount> findByInstitution_IdAndAccountNumber(
            Long institutionId, String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT a FROM BankAccount a "
                    + "WHERE a.institution.id = :institutionId AND a.accountNumber = :accountNumber")
    Optional<BankAccount> findByInstitution_IdAndAccountNumberWithLock(
            @Param("institutionId") Long institutionId,
            @Param("accountNumber") String accountNumber);
}
