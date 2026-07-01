package family.fisa.hangangpay.auth.service;

import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_ACCOUNT_NUMBER;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_ACCOUNT_VERIFIED_AT;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_INSTITUTION_ID;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_PHONE_NUMBER;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_PHONE_VERIFIED;
import static family.fisa.hangangpay.global.session.SessionAttributeNames.SIGNUP_PHONE_VERIFIED_AT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.auth.dto.request.UserRegisterRequest;
import family.fisa.hangangpay.auth.dto.response.UserRegisterResponse;
import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.entity.Wallet;
import family.fisa.hangangpay.domain.wallet.service.WalletCommandService;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
class UserRegistrationServiceTest {

    private static final Long PARTY_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long INSTITUTION_ID = 1L;
    private static final String PHONE_NUMBER = "010-1234-5678";
    private static final String ACCOUNT_NUMBER = "1002123456789";

    @Mock private PartyRepository partyRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private WalletCommandService walletCommandService;
    @Mock private InstitutionQueryService institutionQueryService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private BankClient bankClient;

    @InjectMocks private UserRegistrationService userRegistrationService;

    @Test
    @DisplayName("세션 인증값과 요청값이 일치하면 소비자 회원가입을 완료한다")
    void register() {
        MockHttpSession session = verifiedSession();
        Institution institution =
                Institution.builder().institutionCode("WR").institutionName("우리은행").build();

        given(userRepository.findByPhoneNumberWithParty(PHONE_NUMBER)).willReturn(Optional.empty());
        given(institutionQueryService.getById(INSTITUTION_ID)).willReturn(institution);
        given(passwordEncoder.encode("abc123!@")).willReturn("passwordHash");
        given(passwordEncoder.encode("123456")).willReturn("pinHash");
        given(partyRepository.save(any(Party.class)))
                .willAnswer(
                        invocation -> {
                            Party party = invocation.getArgument(0);
                            ReflectionTestUtils.setField(party, "id", PARTY_ID);
                            return party;
                        });
        given(userRepository.save(any(User.class)))
                .willAnswer(
                        invocation -> {
                            User user = invocation.getArgument(0);
                            ReflectionTestUtils.setField(user, "id", USER_ID);
                            return user;
                        });
        given(accountRepository.save(any(Account.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(walletCommandService.createWallet(any(Party.class), any(Institution.class)))
                .willReturn(
                        Wallet.builder()
                                .party(Party.of(PartyType.USER))
                                .institution(institution)
                                .address("0x1")
                                .build());

        UserRegisterResponse response = userRegistrationService.register(request(), session);

        assertThat(response.partyId()).isEqualTo(PARTY_ID);
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(session.getAttribute(SIGNUP_PHONE_VERIFIED)).isNull();
        assertThat(session.getAttribute(SIGNUP_ACCOUNT_VERIFIED)).isNull();
    }

    @Test
    @DisplayName("휴대폰 인증 세션이 없으면 회원가입을 거부한다")
    void registerWithoutPhoneVerification() {
        MockHttpSession session = new MockHttpSession();

        assertThatThrownBy(() -> userRegistrationService.register(request(), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.SIGNUP_PHONE_NOT_VERIFIED);
    }

    @Test
    @DisplayName("휴대폰 인증이 만료되면 회원가입을 거부한다")
    void registerWithExpiredPhoneVerification() {
        MockHttpSession session = verifiedSession();
        session.setAttribute(SIGNUP_PHONE_VERIFIED_AT, LocalDateTime.now().minusMinutes(31));

        assertThatThrownBy(() -> userRegistrationService.register(request(), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.SIGNUP_PHONE_VERIFICATION_EXPIRED);
    }

    @Test
    @DisplayName("인증한 계좌와 요청 계좌가 다르면 회원가입을 거부한다")
    void registerWithAccountMismatch() {
        MockHttpSession session = verifiedSession();

        assertThatThrownBy(
                        () ->
                                userRegistrationService.register(
                                        request(PHONE_NUMBER, INSTITUTION_ID, "9999999999"),
                                        session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.SIGNUP_ACCOUNT_MISMATCH);
    }

    @Test
    @DisplayName("이미 가입된 휴대폰 번호면 회원가입을 거부한다")
    void registerWithDuplicatePhoneNumber() {
        MockHttpSession session = verifiedSession();
        given(userRepository.findByPhoneNumberWithParty(PHONE_NUMBER))
                .willReturn(Optional.of(User.builder().phoneNumber(PHONE_NUMBER).build()));

        assertThatThrownBy(() -> userRegistrationService.register(request(), session))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.DUPLICATE_PHONE_NUMBER);
    }

    private MockHttpSession verifiedSession() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SIGNUP_PHONE_VERIFIED, true);
        session.setAttribute(SIGNUP_PHONE_NUMBER, PHONE_NUMBER);
        session.setAttribute(SIGNUP_PHONE_VERIFIED_AT, LocalDateTime.now());
        session.setAttribute(SIGNUP_ACCOUNT_VERIFIED, true);
        session.setAttribute(SIGNUP_INSTITUTION_ID, INSTITUTION_ID);
        session.setAttribute(SIGNUP_ACCOUNT_NUMBER, ACCOUNT_NUMBER);
        session.setAttribute(SIGNUP_ACCOUNT_VERIFIED_AT, LocalDateTime.now());
        return session;
    }

    private UserRegisterRequest request() {
        return request(PHONE_NUMBER, INSTITUTION_ID, ACCOUNT_NUMBER);
    }

    private UserRegisterRequest request(
            String phoneNumber, Long institutionId, String accountNumber) {
        return new UserRegisterRequest(
                "홍길동",
                LocalDate.of(1990, 7, 30),
                phoneNumber,
                "abc123!@",
                "123456",
                institutionId,
                accountNumber,
                new UserRegisterRequest.TermsAgreed(true, true, true, true));
    }
}
