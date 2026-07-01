package family.fisa.hangangpay.domain.merchant.repository;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import java.util.List;
import java.util.Optional;

public interface MerchantRepository {
    List<Merchant> findByParty_IdIn(List<Long> payeePartyIds);

    Optional<Merchant> findByParty_Id(Long partyId);

    Optional<Merchant> findByPhoneNumberWithParty(String phoneNumber);

    Optional<Merchant> findByBusinessNumberWithParty(String businessNumber);

    Optional<Merchant> findById(Long merchantId);

    Merchant save(Merchant merchant);

    boolean existsByBusinessNumber(String businessNumber);
}
