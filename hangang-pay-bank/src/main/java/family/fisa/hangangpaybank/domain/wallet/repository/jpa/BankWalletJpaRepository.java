package family.fisa.hangangpaybank.domain.wallet.repository.jpa;

import family.fisa.hangangpaybank.domain.wallet.entity.BankWallet;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankWalletJpaRepository extends JpaRepository<BankWallet, Long> {

    Optional<BankWallet> findByWalletAddress(String walletAddress);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM BankWallet w WHERE w.walletAddress = :walletAddress")
    Optional<BankWallet> findByWalletAddressWithLock(@Param("walletAddress") String walletAddress);
}
