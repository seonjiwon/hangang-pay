package family.fisa.hangangpaybank.domain.wallet.repository;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import family.fisa.hangangpaybank.domain.wallet.repository.jpa.WalletLedgerJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletLedgerRepositoryImpl implements WalletLedgerRepository {

    private final WalletLedgerJpaRepository jpaRepository;

    @Override
    public WalletLedger save(WalletLedger walletLedger) {
        return jpaRepository.save(walletLedger);
    }

    @Override
    public Optional<WalletLedger> findFirstByTransactionUuid(String transactionUuid) {
        return jpaRepository.findFirstByTransactionUuid(transactionUuid);
    }
}
