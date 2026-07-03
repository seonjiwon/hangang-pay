package family.fisa.hangangpaybank.domain.ledger.repository;

import family.fisa.hangangpaybank.domain.ledger.entity.AccountLedger;
import java.util.Optional;

public interface AccountLedgerRepository {
    AccountLedger save(AccountLedger ledger);

    Optional<AccountLedger> findByIdempotentKey(String idempotentKey);
}
