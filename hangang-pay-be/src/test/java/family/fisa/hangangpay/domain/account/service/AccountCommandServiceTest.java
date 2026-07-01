package family.fisa.hangangpay.domain.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.BankAccountCreateRequest;
import family.fisa.hangangpay.client.bank.dto.response.BankAccountResponse;
import family.fisa.hangangpay.domain.account.dto.request.AccountCreateRequest;
import family.fisa.hangangpay.domain.account.dto.response.AccountResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AccountCommandServiceTest {

    private static final Long PARTY_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long INSTITUTION_ID = 20L;
    private static final String INSTITUTION_CODE = "WR";
    private static final String ACCOUNT_NUMBER = "1002123456789";
    private static final String OWNER_NAME = "홍길동";

    @Mock private AccountRepository accountRepository;
    @Mock private MerchantRepository merchantRepository;
    @Mock private PartyRepository partyRepository;
    @Mock private InstitutionQueryService institutionQueryService;
    @Mock private BankClient bankClient;
    @Mock private UserQueryService userQueryService;
    @Mock private MerchantQueryService merchantQueryService;

    @InjectMocks private AccountCommandService accountCommandService;

    @Test
    @DisplayName("사용자 계좌 추가 시 은행 계좌를 새로 생성한다")
    void addAccountCreatesBankAccount() {
        AccountCreateRequest request = new AccountCreateRequest(INSTITUTION_CODE, ACCOUNT_NUMBER);

        Institution institution =
                Institution.builder()
                        .id(INSTITUTION_ID)
                        .institutionCode(INSTITUTION_CODE)
                        .institutionName("우리은행")
                        .build();
        Party party = Party.of(PartyType.USER);
        ReflectionTestUtils.setField(party, "id", PARTY_ID);
        User user =
                User.builder()
                        .party(party)
                        .username(OWNER_NAME)
                        .passwordHash("passwordHash")
                        .paymentPinHash("pinHash")
                        .phoneNumber("010-1234-5678")
                        .build();

        given(institutionQueryService.getByCode(INSTITUTION_CODE)).willReturn(institution);
        given(accountRepository.existsByParty_IdAndAccountNumber(PARTY_ID, ACCOUNT_NUMBER))
                .willReturn(false);
        given(accountRepository.countByParty_Id(PARTY_ID)).willReturn(1L);
        given(partyRepository.getReferenceById(PARTY_ID)).willReturn(party);
        given(userQueryService.getByPartyId(PARTY_ID)).willReturn(user);
        given(accountRepository.save(any(Account.class)))
                .willAnswer(
                        invocation -> {
                            Account account = invocation.getArgument(0);
                            ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
                            return account;
                        });
        given(bankClient.createBankAccount(any(BankAccountCreateRequest.class)))
                .willReturn(
                        new BankAccountResponse(
                                ACCOUNT_ID,
                                INSTITUTION_ID,
                                ACCOUNT_NUMBER,
                                new BigDecimal("1000000"),
                                OWNER_NAME));

        AccountResponse response = accountCommandService.createAccount(PARTY_ID, request);

        ArgumentCaptor<BankAccountCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(BankAccountCreateRequest.class);
        verify(bankClient).createBankAccount(requestCaptor.capture());
        verify(bankClient, never()).getBankAccount(anyLong(), anyString());

        BankAccountCreateRequest bankRequest = requestCaptor.getValue();
        assertThat(bankRequest.institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(bankRequest.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(bankRequest.ownerName()).isEqualTo(OWNER_NAME);
        assertThat(bankRequest.initialBalance()).isEqualByComparingTo("1000000");
        assertThat(response.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.accountType()).isEqualTo("SECONDARY");
    }

    @Test
    @DisplayName("가맹점 계좌 추가 시 대표자명과 초기 잔액 0원으로 은행 계좌를 생성한다")
    void addAccountCreatesMerchantBankAccount() {
        AccountCreateRequest request = new AccountCreateRequest(INSTITUTION_CODE, ACCOUNT_NUMBER);

        Institution institution =
                Institution.builder()
                        .id(INSTITUTION_ID)
                        .institutionCode(INSTITUTION_CODE)
                        .institutionName("우리은행")
                        .build();

        Party party = Party.of(PartyType.MERCHANT);
        ReflectionTestUtils.setField(party, "id", PARTY_ID);

        Merchant merchant =
                Merchant.builder()
                        .party(party)
                        .username("merchant")
                        .passwordHash("passwordHash")
                        .paymentPinHash("pinHash")
                        .businessNumber("1234567890")
                        .merchantName("한강상점")
                        .ownerName("상점주")
                        .phoneNumber("010-1111-2222")
                        .build();

        given(institutionQueryService.getByCode(INSTITUTION_CODE)).willReturn(institution);
        given(accountRepository.existsByParty_IdAndAccountNumber(PARTY_ID, ACCOUNT_NUMBER))
                .willReturn(false);
        given(accountRepository.countByParty_Id(PARTY_ID)).willReturn(1L);
        given(partyRepository.getReferenceById(PARTY_ID)).willReturn(party);
        given(merchantQueryService.getByPartyId(PARTY_ID)).willReturn(merchant);
        given(accountRepository.save(any(Account.class)))
                .willAnswer(
                        invocation -> {
                            Account account = invocation.getArgument(0);
                            ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
                            return account;
                        });

        given(bankClient.createBankAccount(any(BankAccountCreateRequest.class)))
                .willReturn(
                        new BankAccountResponse(
                                ACCOUNT_ID,
                                INSTITUTION_ID,
                                ACCOUNT_NUMBER,
                                BigDecimal.ZERO,
                                "상점주"));

        AccountResponse response = accountCommandService.createAccount(PARTY_ID, request);

        ArgumentCaptor<BankAccountCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(BankAccountCreateRequest.class);

        verify(bankClient).createBankAccount(requestCaptor.capture());
        verify(bankClient, never()).getBankAccount(anyLong(), anyString());
        verify(userQueryService, never()).getByPartyId(anyLong());
        verify(merchantQueryService).getByPartyId(PARTY_ID);

        BankAccountCreateRequest bankRequest = requestCaptor.getValue();
        assertThat(bankRequest.institutionId()).isEqualTo(INSTITUTION_ID);
        assertThat(bankRequest.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(bankRequest.ownerName()).isEqualTo("상점주");
        assertThat(bankRequest.initialBalance()).isEqualByComparingTo("0");

        assertThat(response.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(response.accountType()).isEqualTo("SECONDARY");
    }
}
