package family.fisa.hangangpaybank.domain.ledger.repository.jpa;

import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLedgerJpaRepository extends JpaRepository<AccountLedger, Long> {

    Optional<AccountLedger> findByIdempotentKey(String idempotentKey);
}
