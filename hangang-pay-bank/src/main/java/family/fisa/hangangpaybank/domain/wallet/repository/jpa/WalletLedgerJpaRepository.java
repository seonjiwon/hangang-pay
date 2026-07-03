package family.fisa.hangangpaybank.domain.wallet.repository.jpa;

import family.fisa.hangangpaybank.domain.wallet.entity.WalletLedger;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletLedgerJpaRepository extends JpaRepository<WalletLedger, Long> {

    Optional<WalletLedger> findFirstByTransactionUuid(String transactionUuid);
}
