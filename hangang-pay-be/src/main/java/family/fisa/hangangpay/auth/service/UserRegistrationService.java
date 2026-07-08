package family.fisa.hangangpay.auth.service;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.auth.dto.request.UserRegisterRequest;
import family.fisa.hangangpay.auth.dto.response.UserRegisterResponse;
import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.BankAccountCreateRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.repository.UserRepository;
import family.fisa.hangangpay.domain.wallet.service.WalletCommandService;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserRegistrationService {

    private final PartyRepository partyRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final WalletCommandService walletCommandService;
    private final InstitutionQueryService institutionQueryService;
    private final PasswordEncoder passwordEncoder;
    private final BankClient bankClient;

    public UserRegisterResponse register(UserRegisterRequest request) {
        // TODO: existsBy 방식으로 바꾸기 - fetch join 까지 필요없음
        userRepository
                .findByPhoneNumberWithParty(request.phoneNumber())
                .ifPresent(
                        user -> {
                            throw new BusinessException(AuthErrorCode.DUPLICATE_PHONE_NUMBER);
                        });

        Institution institution = institutionQueryService.getById(request.institutionId());

        Party party = partyRepository.save(Party.of(PartyType.USER));
        User user =
                userRepository.save(
                        User.builder()
                                .party(party)
                                .username(request.name())
                                .passwordHash(passwordEncoder.encode(request.password()))
                                .paymentPinHash(passwordEncoder.encode(request.paymentPin()))
                                .phoneNumber(request.phoneNumber())
                                .build());

        accountRepository.save(
                Account.builder()
                        .party(party)
                        .institution(institution)
                        .accountType(AccountType.PRIMARY)
                        .accountNumber(request.accountNumber())
                        .build());
        bankClient.createBankAccount(
                new BankAccountCreateRequest(
                        institution.getId(),
                        request.accountNumber(),
                        request.name(),
                        new BigDecimal(1_000_000)));
        walletCommandService.createWallet(party, institution);

        log.info("소비자 회원가입 완료: userId={}, partyId={}", user.getId(), party.getId());
        return new UserRegisterResponse(party.getId(), user.getId());
    }
}
