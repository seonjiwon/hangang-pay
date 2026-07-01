package family.fisa.hangangpay.domain.user.repository;

import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.jpa.UserJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserJpaRepository userJpaRepository;

    @Override
    public Optional<User> findByIdWithParty(Long userId) {
        return userJpaRepository.findByIdWithParty(userId);
    }

    @Override
    public Optional<User> findByPhoneNumberWithParty(String phoneNumber) {
        return userJpaRepository.findByPhoneNumberWithParty(phoneNumber);
    }

    @Override
    public List<User> findByParty_IdIn(List<Long> partyIds) {
        return userJpaRepository.findByParty_IdIn(partyIds);
    }

    @Override
    public Optional<User> findByParty_Id(Long partyId) {
        return userJpaRepository.findByParty_Id(partyId);
    }

    @Override
    public User save(User user) {
        return userJpaRepository.save(user);
    }
}
