package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletJpaRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByParty_Id(Long partyId);

    Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId);

    Wallet save(Wallet wallet);
}
