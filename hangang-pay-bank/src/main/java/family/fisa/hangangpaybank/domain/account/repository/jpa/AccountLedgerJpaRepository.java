package family.fisa.hangangpaybank.domain.account.repository.jpa;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerJpaRepository extends JpaRepository<AccountLedger, Long> {

    Optional<AccountLedger> findByIdempotentKey(String idempotentKey);
}
