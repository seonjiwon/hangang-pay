package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class WalletRepositoryImpl implements WalletRepository {

    private final WalletJpaRepository walletJpaRepository;

    @Override
    public Optional<Wallet> findByParty_Id(Long partyId) {
        return walletJpaRepository.findByParty_Id(partyId);
    }

    @Override
    public Optional<Wallet> findByParty_IdForUpdate(Long partyId) {
        return walletJpaRepository.findByParty_IdForUpdate(partyId);
    }

    @Override
    public Optional<Wallet> findByParty_IdForUpdateNoWait(Long partyId) {
        return walletJpaRepository.findByParty_IdForUpdateNoWait(partyId);
    }

    @Override
    public Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId) {
        return walletJpaRepository.findByIdAndParty_Id(id, partyId);
    }

    @Override
    public Optional<Wallet> findById(Long id) {
        return walletJpaRepository.findById(id);
    }

    @Override
    public Wallet save(Wallet wallet) {
        return walletJpaRepository.save(wallet);
    }
}
