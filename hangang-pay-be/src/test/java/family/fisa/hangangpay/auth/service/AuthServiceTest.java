package family.fisa.hangangpay.auth.service;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.MERCHANT_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.PARTY_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.ROLE;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.USER_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.auth.dto.request.LoginRequest;
import family.fisa.hangangpay.auth.dto.request.MerchantLoginRequest;
import family.fisa.hangangpay.auth.dto.response.LoginResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PHONE_NUMBER = "010-1234-5678";
    private static final String BUSINESS_NUMBER = "123-45-67890";
    private static final String RAW_PASSWORD = "password1234";
    private static final String PASSWORD_HASH = "{bcrypt}hash";

    @Mock private UserRepository userRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AuthService authService;

    @Test
    @DisplayName("사용자 로그인 성공 시 세션과 응답을 생성한다")
    void loginUser() {
        MockHttpSession session = new MockHttpSession();
        User user = user(1L, 10L);

        given(userRepository.findByPhoneNumberWithParty(PHONE_NUMBER))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        LoginResponse response =
                authService.loginUser(new LoginRequest(PHONE_NUMBER, RAW_PASSWORD), session);

        assertThat(response.principalId()).isEqualTo(1L);
        assertThat(response.partyId()).isEqualTo(10L);
        assertThat(response.role()).isEqualTo(PartyType.USER);
        assertThat(session.getAttribute(USER_ID)).isEqualTo(1L);
        assertThat(session.getAttribute(MERCHANT_ID)).isNull();
        assertThat(session.getAttribute(PARTY_ID)).isEqualTo(10L);
        assertThat(session.getAttribute(ROLE)).isEqualTo(PartyType.USER.name());
    }

    @Test
    @DisplayName("가맹점 로그인 성공 시 세션과 응답을 생성한다")
    void loginMerchant() {
        MockHttpSession session = new MockHttpSession();
        Merchant merchant = merchant(2L, 20L);

        given(merchantRepository.findByBusinessNumberWithParty(BUSINESS_NUMBER))
                .willReturn(Optional.of(merchant));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        LoginResponse response =
                authService.loginMerchant(
                        new MerchantLoginRequest(BUSINESS_NUMBER, RAW_PASSWORD), session);

        assertThat(response.principalId()).isEqualTo(2L);
        assertThat(response.partyId()).isEqualTo(20L);
        assertThat(response.role()).isEqualTo(PartyType.MERCHANT);
        assertThat(session.getAttribute(USER_ID)).isNull();
        assertThat(session.getAttribute(MERCHANT_ID)).isEqualTo(2L);
        assertThat(session.getAttribute(PARTY_ID)).isEqualTo(20L);
        assertThat(session.getAttribute(ROLE)).isEqualTo(PartyType.MERCHANT.name());
    }

    @Test
    @DisplayName("새 로그인 시 기존 인증 세션 값을 제거한다")
    void loginClearsPreviousAuthSessionAttributes() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(MERCHANT_ID, 999L);
        session.setAttribute(PARTY_ID, 999L);
        session.setAttribute(ROLE, PartyType.MERCHANT.name());
        User user = user(1L, 10L);

        given(userRepository.findByPhoneNumberWithParty(PHONE_NUMBER))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(true);

        authService.loginUser(new LoginRequest(PHONE_NUMBER, RAW_PASSWORD), session);

        assertThat(session.getAttribute(USER_ID)).isEqualTo(1L);
        assertThat(session.getAttribute(MERCHANT_ID)).isNull();
        assertThat(session.getAttribute(PARTY_ID)).isEqualTo(10L);
        assertThat(session.getAttribute(ROLE)).isEqualTo(PartyType.USER.name());
    }

    @Test
    @DisplayName("전화번호나 비밀번호가 비어 있으면 INVALID_CREDENTIALS 예외를 던진다")
    void loginWithBlankRequest() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> authService.loginUser(new LoginRequest("", RAW_PASSWORD), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

        assertThatThrownBy(
                        () -> authService.loginUser(new LoginRequest(PHONE_NUMBER, " "), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("전화번호로 사용자를 찾을 수 없으면 INVALID_CREDENTIALS 예외를 던진다")
    void loginUserNotFound() {
        MockHttpSession session = new MockHttpSession();
        given(userRepository.findByPhoneNumberWithParty(PHONE_NUMBER)).willReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                authService.loginUser(
                                        new LoginRequest(PHONE_NUMBER, RAW_PASSWORD), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("비밀번호가 일치하지 않으면 INVALID_CREDENTIALS 예외를 던진다")
    void loginWithInvalidPassword() {
        MockHttpSession session = new MockHttpSession();
        User user = user(1L, 10L);

        given(userRepository.findByPhoneNumberWithParty(PHONE_NUMBER))
                .willReturn(Optional.of(user));
        given(passwordEncoder.matches(RAW_PASSWORD, PASSWORD_HASH)).willReturn(false);

        assertThatThrownBy(
                        () ->
                                authService.loginUser(
                                        new LoginRequest(PHONE_NUMBER, RAW_PASSWORD), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    @DisplayName("로그아웃 시 세션을 만료한다")
    void logout() {
        MockHttpSession session = new MockHttpSession();

        authService.logout(session);

        assertThat(session.isInvalid()).isTrue();
    }

    @Test
    @DisplayName("세션이 없으면 로그아웃을 무시한다")
    void logoutWithNullSession() {
        authService.logout(null);
    }

    private User user(Long userId, Long partyId) {
        Party party = Party.of(PartyType.USER);
        ReflectionTestUtils.setField(party, "id", partyId);

        User user =
                User.builder()
                        .party(party)
                        .username("김한강")
                        .passwordHash(PASSWORD_HASH)
                        .phoneNumber(PHONE_NUMBER)
                        .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Merchant merchant(Long merchantId, Long partyId) {
        Party party = Party.of(PartyType.MERCHANT);
        ReflectionTestUtils.setField(party, "id", partyId);

        Merchant merchant =
                Merchant.builder()
                        .party(party)
                        .username("성동상점")
                        .passwordHash(PASSWORD_HASH)
                        .businessNumber("123-45-67890")
                        .merchantName("성동상점")
                        .ownerName("김사장")
                        .phoneNumber(PHONE_NUMBER)
                        .build();
        ReflectionTestUtils.setField(merchant, "id", merchantId);
        return merchant;
    }
}
