package family.fisa.hangangpaybank.domain.ledger.repository;

import family.fisa.hangangpaybank.domain.ledger.entity.WalletLedger;
import java.util.Optional;

/** wallet_ledger 도메인 저장소 포트. 구현은 {@code WalletLedgerRepositoryImpl}. */
public interface WalletLedgerRepository {

    WalletLedger save(WalletLedger walletLedger);

    Optional<WalletLedger> findFirstByTransactionUuid(String transactionUuid);
}
