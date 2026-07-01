package family.fisa.hangangpay.domain.user.service;

import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.response.UserProfileResponse;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;

    public UserProfileResponse getProfile(Long partyId) {
        User user =
                userRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));

        return UserProfileResponse.from(user);
    }

    @Transactional(readOnly = true)
    public User getByPartyId(Long partyId) {
        return userRepository
                .findByParty_Id(partyId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
    }
}
