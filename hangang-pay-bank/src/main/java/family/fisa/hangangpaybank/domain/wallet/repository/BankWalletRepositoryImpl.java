package family.fisa.hangangpaybank.domain.wallet.repository;

import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import family.fisa.hangangpaybank.domain.wallet.repository.jpa.BankWalletJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BankWalletRepositoryImpl implements BankWalletRepository {

    private final BankWalletJpaRepository jpaRepository;

    @Override
    public BankWallet save(BankWallet bankWallet) {
        return jpaRepository.save(bankWallet);
    }

    @Override
    public Optional<BankWallet> findByWalletAddress(String walletAddress) {
        return jpaRepository.findByWalletAddress(walletAddress);
    }

    @Override
    public Optional<BankWallet> findByWalletAddressWithLock(String walletAddress) {
        return jpaRepository.findByWalletAddressWithLock(walletAddress);
    }
}
