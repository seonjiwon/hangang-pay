package family.fisa.hangangpaybank.domain.account.repository;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import java.util.Optional;

public interface AccountLedgerRepository {
    AccountLedger save(AccountLedger ledger);

    Optional<AccountLedger> findByIdempotentKey(String idempotentKey);
}
