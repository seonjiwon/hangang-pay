package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByParty_Id(Long partyId);

    Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId);

    Optional<Wallet> findById(Long id);

    Wallet save(Wallet wallet);
}
