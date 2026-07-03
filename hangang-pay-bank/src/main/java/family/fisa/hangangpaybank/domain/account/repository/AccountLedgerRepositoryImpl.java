package family.fisa.hangangpaybank.domain.account.repository;

import family.fisa.hangangpaybank.domain.account.entity.AccountLedger;
import family.fisa.hangangpaybank.domain.account.repository.jpa.AccountLedgerJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountLedgerRepositoryImpl implements AccountLedgerRepository {

    private final AccountLedgerJpaRepository jpaRepository;

    @Override
    public AccountLedger save(AccountLedger ledger) {
        return jpaRepository.save(ledger);
    }

    @Override
    public Optional<AccountLedger> findByIdempotentKey(String idempotentKey) {
        return jpaRepository.findByIdempotentKey(idempotentKey);
    }
}
