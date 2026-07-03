package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.BankWallet;
import java.util.Optional;

/** bank_wallet 도메인 저장소 포트. 구현은 {@code BankWalletRepositoryImpl}. */
public interface BankWalletRepository {

    BankWallet save(BankWallet bankWallet);

    Optional<BankWallet> findByWalletAddress(String walletAddress);

    Optional<BankWallet> findByWalletAddressWithLock(String walletAddress);
}
