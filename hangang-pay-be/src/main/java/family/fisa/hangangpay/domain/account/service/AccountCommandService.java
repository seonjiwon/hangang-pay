package family.fisa.hangangpay.domain.account.service;

import family.fisa.hangangpay.client.bank.BankClient;
import family.fisa.hangangpay.client.bank.dto.request.BankAccountCreateRequest;
import family.fisa.hangangpay.client.bank.dto.response.BankAccountResponse;
import family.fisa.hangangpay.domain.account.dto.request.AccountCreateRequest;
import family.fisa.hangangpay.domain.account.dto.request.MerchantAccountUpdateRequest;
import family.fisa.hangangpay.domain.account.dto.response.AccountResponse;
import family.fisa.hangangpay.domain.account.dto.response.MerchantAccountUpdateResponse;
import family.fisa.hangangpay.domain.account.dto.response.PrimaryAccountResponse;
import family.fisa.hangangpay.domain.account.entity.Account;
import family.fisa.hangangpay.domain.account.entity.AccountType;
import family.fisa.hangangpay.domain.account.repository.AccountRepository;
import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.service.InstitutionQueryService;
import family.fisa.hangangpay.domain.merchant.code.MerchantErrorCode;
import family.fisa.hangangpay.domain.merchant.entity.Merchant;
import family.fisa.hangangpay.domain.merchant.repository.MerchantRepository;
import family.fisa.hangangpay.domain.merchant.service.MerchantQueryService;
import family.fisa.hangangpay.domain.party.entity.Party;
import family.fisa.hangangpay.domain.party.entity.PartyType;
import family.fisa.hangangpay.domain.party.repository.PartyRepository;
import family.fisa.hangangpay.domain.user.entity.User;
import family.fisa.hangangpay.domain.user.service.UserQueryService;
import family.fisa.hangangpay.global.code.error.AccountErrorCode;
import family.fisa.hangangpay.global.exception.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 쓰기 서비스 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccountCommandService {

    private static final BigDecimal USER_ACCOUNT_INITIAL_BALANCE = new BigDecimal(1_000_000);
    private static final BigDecimal MERCHANT_ACCOUNT_INITIAL_BALANCE = BigDecimal.ZERO;

    private final AccountRepository accountRepository;
    private final MerchantRepository merchantRepository;
    private final PartyRepository partyRepository;
    private final InstitutionQueryService institutionQueryService;
    private final BankClient bankClient;
    private final UserQueryService userQueryService;
    private final MerchantQueryService merchantQueryService;

    /** 계좌 추가 메서드 */
    public AccountResponse createAccount(Long partyId, AccountCreateRequest request) {
        // 1. 기관 코드로 BE 캐시에서 institution 조회
        Institution institution = institutionQueryService.getByCode(request.institutionCode());

        // 2. 동일 계좌 중복 등록 여부 확인
        if (accountRepository.existsByParty_IdAndAccountNumber(partyId, request.accountNumber())) {
            throw new BusinessException(AccountErrorCode.DUPLICATE_ACCOUNT);
        }

        // 3. 등록 계좌 수 조회 - 한도 초과 확인
        long count = accountRepository.countByParty_Id(partyId);
        if (count >= 3) {
            throw new BusinessException(AccountErrorCode.MAX_ACCOUNT_EXCEEDED);
        }

        // 4. 회원가입 시 생성된 주거래 계좌 이후 추가되는 계좌는 일반 계좌로 등록
        AccountType accountType = AccountType.SECONDARY;

        // 5. 파티 프록시 참조 로드
        Party party = partyRepository.getReferenceById(partyId);
        AccountOwner accountOwner = resolveAccountOwner(partyId, party.getPartyType());

        // 6. 계좌 저장
        Account account =
                Account.builder()
                        .party(party)
                        .institution(institution)
                        .accountType(accountType)
                        .accountNumber(request.accountNumber())
                        .build();
        Account saved = accountRepository.save(account);

        bankClient.createBankAccount(
                new BankAccountCreateRequest(
                        institution.getId(),
                        request.accountNumber(),
                        accountOwner.ownerName(),
                        accountOwner.initialBalance()));
        log.info(
                "계좌 추가 완료: partyId={}, accountId={}, accountType={}",
                partyId,
                saved.getId(),
                accountType);

        return AccountResponse.from(saved);
    }

    /** 계좌 삭제 메서드 */
    public void deleteAccount(Long partyId, Long accountId) {
        Account account =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        long count = accountRepository.countByParty_Id(partyId);
        if (count <= 1) {
            throw new BusinessException(AccountErrorCode.LAST_ACCOUNT_DELETE);
        }

        if (account.getAccountType() == AccountType.PRIMARY) {
            throw new BusinessException(AccountErrorCode.PRIMARY_ACCOUNT_DELETE);
        }

        accountRepository.delete(account);
        log.info("계좌 삭제 완료: partyId={}, accountId={}", partyId, accountId);
    }

    /** 주거래 계좌 변경 메서드 */
    public PrimaryAccountResponse updatePrimaryAccount(Long partyId, Long accountId) {
        Account target =
                accountRepository
                        .findByIdAndParty_Id(accountId, partyId)
                        .orElseThrow(
                                () -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        Long previousPrimaryAccountId = null;
        Optional<Account> currentPrimary =
                accountRepository.findByParty_IdAndAccountType(partyId, AccountType.PRIMARY);
        if (currentPrimary.isPresent() && !currentPrimary.get().getId().equals(accountId)) {
            previousPrimaryAccountId = currentPrimary.get().getId();
            currentPrimary.get().updateAccountType(AccountType.SECONDARY);
        }

        target.updateAccountType(AccountType.PRIMARY);
        log.info(
                "주거래 계좌 변경 완료: partyId={}, accountId={}, previousPrimaryId={}",
                partyId,
                accountId,
                previousPrimaryAccountId);

        return new PrimaryAccountResponse(
                target.getId(), AccountType.PRIMARY.name(), previousPrimaryAccountId);
    }

    /** 가맹점 정산 계좌 수정 또는 생성 */
    public MerchantAccountUpdateResponse updateMerchantSettlementAccount(
            Long partyId, MerchantAccountUpdateRequest request) {

        // 1. 가맹점 조회
        Merchant merchant =
                merchantRepository
                        .findByParty_Id(partyId)
                        .orElseThrow(
                                () -> new BusinessException(MerchantErrorCode.MERCHANT_NOT_FOUND));

        Institution institution = institutionQueryService.getByCode(request.institutionCode());

        // 3. 은행에서 계좌 가져오기 -> 존재 하는 지 확인
        BankAccountResponse accountResponse =
                bankClient.getBankAccount(institution.getId(), request.accountNumber());

        if (accountResponse == null) {
            throw new BusinessException(AccountErrorCode.BANK_ACCOUNT_NOT_FOUND);
        }

        // 4. 정산 계좌가 았으면 수정, 없으면 생성
        Optional<Account> existing =
                accountRepository.findByParty_IdAndAccountType(partyId, AccountType.SETTLEMENT);
        Account account;

        // 계좌가 존재한는 경우 업데이트
        if (existing.isPresent()) {
            account = existing.get();
            account.updateBankAccount(institution, request.accountNumber());

            log.info(
                    "가맹점 정산 계좌 수정: partyId={}, accountId={}, institutionCode={}",
                    partyId,
                    account.getId(),
                    institution.getInstitutionCode());

        } else { // 계좌가 존재하지 않는 경우 새로 생성
            account =
                    Account.builder()
                            .party(merchant.getParty())
                            .institution(institution)
                            .accountType(AccountType.SETTLEMENT)
                            .accountNumber(request.accountNumber())
                            .build();
            account = accountRepository.save(account);

            log.info(
                    "가맹점 정산 계좌 생성: partyId={}, accountId={}, institutionCode={}",
                    partyId,
                    account.getId(),
                    institution.getInstitutionCode());
        }

        return MerchantAccountUpdateResponse.from(account);
    }

    private AccountOwner resolveAccountOwner(Long partyId, PartyType partyType) {
        if (partyType == PartyType.USER) {
            User user = userQueryService.getByPartyId(partyId);
            return new AccountOwner(user.getUsername(), USER_ACCOUNT_INITIAL_BALANCE);
        }

        Merchant merchant = merchantQueryService.getByPartyId(partyId);
        return new AccountOwner(merchant.getOwnerName(), MERCHANT_ACCOUNT_INITIAL_BALANCE);
    }

    private record AccountOwner(String ownerName, BigDecimal initialBalance) {}
}
