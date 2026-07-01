package family.fisa.hangangpay.domain.user.repository;

import family.fisa.hangangpay.domain.user.entity.User;
import java.util.List;
import java.util.Optional;

public interface UserRepository {

    Optional<User> findByIdWithParty(Long userId);

    Optional<User> findByPhoneNumberWithParty(String phoneNumber);

    List<User> findByParty_IdIn(List<Long> partyIds);

    Optional<User> findByParty_Id(Long partyId);

    User save(User user);
}
