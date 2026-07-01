package family.fisa.hangangpay.domain.merchant.repository;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.jpa.MerchantJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class MerchantRepositoryImpl implements MerchantRepository {

    private final MerchantJpaRepository merchantJpaRepository;

    @Override
    public List<Merchant> findByParty_IdIn(List<Long> payeePartyIds) {
        return merchantJpaRepository.findByParty_IdIn(payeePartyIds);
    }

    @Override
    public Optional<Merchant> findByParty_Id(Long partyId) {
        return merchantJpaRepository.findByParty_Id(partyId);
    }

    @Override
    public Optional<Merchant> findByPhoneNumberWithParty(String phoneNumber) {
        return merchantJpaRepository.findByPhoneNumberWithParty(phoneNumber);
    }

    @Override
    public Optional<Merchant> findById(Long merchantId) {
        return merchantJpaRepository.findById(merchantId);
    }

    @Override
    public Merchant save(Merchant merchant) {
        return merchantJpaRepository.save(merchant);
    }

    @Override
    public Optional<Merchant> findByBusinessNumberWithParty(String businessNumber) {
        return merchantJpaRepository.findByBusinessNumberWithParty(businessNumber);
    }

    @Override
    public boolean existsByBusinessNumber(String businessNumber) {
        return merchantJpaRepository.existsByBusinessNumber(businessNumber);
    }
}
