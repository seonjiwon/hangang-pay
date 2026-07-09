package family.fisa.hangangpay.domain.wallet.repository;

import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import java.util.Optional;

public interface WalletRepository {

    Optional<Wallet> findByParty_Id(Long partyId);

    /** 비관적 락 - 동일 파티의 동시 요청을 직렬화 */
    Optional<Wallet> findByParty_IdForUpdate(Long partyId);

    /** 비관적 락(NOWAIT) - 락 소유자가 있으면 즉시 실패 */
    Optional<Wallet> findByParty_IdForUpdateNoWait(Long partyId);

    Optional<Wallet> findByIdAndParty_Id(Long id, Long partyId);

    Optional<Wallet> findById(Long id);

    Wallet save(Wallet wallet);
}
