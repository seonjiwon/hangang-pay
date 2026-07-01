package family.fisa.hangangpay.domain.merchant.repository.jpa;

import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MerchantJpaRepository extends JpaRepository<Merchant, Long> {
    List<Merchant> findByParty_IdIn(List<Long> payeePartyIds);

    Optional<Merchant> findByParty_Id(Long partyId);

    @Query("select m from Merchant m join fetch m.party where m.phoneNumber = :phoneNumber")
    Optional<Merchant> findByPhoneNumberWithParty(@Param("phoneNumber") String phoneNumber);

    @Query("select m from Merchant m join fetch m.party where m.businessNumber = :businessNumber")
    Optional<Merchant> findByBusinessNumberWithParty(
            @Param("businessNumber") String businessNumber);

    boolean existsByBusinessNumber(String businessNumber);
}
