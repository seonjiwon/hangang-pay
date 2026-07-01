package family.fisa.hangangpay.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.user.code.UserErrorCode;
import family.fisa.hangangpay.domain.user.dto.response.UserProfileResponse;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks private UserQueryService userQueryService;

    @Test
    @DisplayName("사용자 프로필 응답을 생성한다")
    void getProfile() {
        Party party = Party.of(PartyType.USER);
        ReflectionTestUtils.setField(party, "id", 10L);

        User user =
                User.builder()
                        .party(party)
                        .username("김한강")
                        .phoneNumber("010-1234-5678")
                        .birthDate(LocalDate.of(1999, 5, 16))
                        .region("성동구")
                        .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        given(userRepository.findByParty_Id(10L)).willReturn(Optional.of(user));

        UserProfileResponse response = userQueryService.getProfile(10L);

        assertThat(response.userId()).isEqualTo(1L);
        assertThat(response.partyId()).isEqualTo(10L);
        assertThat(response.username()).isEqualTo("김한강");
        assertThat(response.phoneNumber()).isEqualTo("010-1234-5678");
        assertThat(response.birthDate()).isEqualTo(LocalDate.of(1999, 5, 16));
        assertThat(response.region()).isEqualTo("성동구");
    }

    @Test
    @DisplayName("사용자를 찾을 수 없으면 USER_NOT_FOUND 예외를 던진다")
    void getProfileUserNotFound() {
        given(userRepository.findByParty_Id(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userQueryService.getProfile(999L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(UserErrorCode.USER_NOT_FOUND);
    }
}
