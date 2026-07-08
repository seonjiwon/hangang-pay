package family.fisa.hangangpay.auth.service;

import family.fisa.hangangpay.auth.code.AuthErrorCode;
import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.BankAccountCreateRequest;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.merchant.dto.request.MerchantRegisterRequest;
import family.fisa.hangangpay.domain.merchant.dto.response.BusinessInfoResponse;
import family.fisa.hangangpay.domain.merchant.dto.response.MerchantRegisterResponse;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
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
public class MerchantRegistrationService {

    private final PartyRepository partyRepository;
    private final MerchantRepository merchantRepository;
    private final AccountRepository accountRepository;
    private final WalletCommandService walletCommandService;
    private final InstitutionQueryService institutionQueryService;
    private final BusinessInfoQueryService businessInfoQueryService;
    private final PasswordEncoder passwordEncoder;
    private final BankClient bankClient;

    public MerchantRegisterResponse register(MerchantRegisterRequest request) {
        BusinessInfoResponse businessInfo =
                businessInfoQueryService.getBusinessInfo(request.businessNumber());

        if (merchantRepository.existsByBusinessNumber(request.businessNumber())) {
            throw new BusinessException(AuthErrorCode.DUPLICATE_BUSINESS_NUMBER);
        }

        Institution institution = institutionQueryService.getById(request.institutionId());

        Party party = partyRepository.save(Party.of(PartyType.MERCHANT));
        Merchant merchant =
                merchantRepository.save(
                        Merchant.builder()
                                .party(party)
                                .username(request.username())
                                .passwordHash(passwordEncoder.encode(request.password()))
                                .paymentPinHash(passwordEncoder.encode(request.paymentPin()))
                                .businessNumber(request.businessNumber())
                                .merchantName(businessInfo.merchantName())
                                .ownerName(businessInfo.ownerName())
                                .address(businessInfo.address())
                                .phoneNumber(request.phoneNumber())
                                .build());

        accountRepository.save(
                Account.builder()
                        .party(party)
                        .institution(institution)
                        .accountType(AccountType.SETTLEMENT)
                        .accountNumber(request.accountNumber())
                        .build());
        bankClient.createBankAccount(
                new BankAccountCreateRequest(
                        institution.getId(),
                        request.accountNumber(),
                        businessInfo.ownerName(),
                        BigDecimal.ZERO));
        walletCommandService.createWallet(party, institution);

        log.info("가맹점 회원가입 완료: merchantId={}, partyId={}", merchant.getId(), party.getId());
        return new MerchantRegisterResponse(
                party.getId(), merchant.getId(), merchant.getMerchantName());
    }
}
