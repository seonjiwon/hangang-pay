package family.fisa.hangangpaybank.domain.account.service;

import family.fisa.hangangpaybank.domain.institution.code.InstitutionErrorCode;
import family.fisa.hangangpaybank.domain.account.dto.request.CreateBankAccountRequest;
import family.fisa.hangangpaybank.domain.account.dto.response.BankAccountResponse;
import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.domain.institution.repository.InstitutionRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BankAccountCommandService {

    private final BankAccountRepository bankAccountRepository;
    private final InstitutionRepository institutionRepository;

    public BankAccountResponse create(CreateBankAccountRequest request) {
        // 1. 소속 기관 조회
        Institution institution =
                institutionRepository
                        .findById(request.institutionId())
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                InstitutionErrorCode.INSTITUTION_NOT_FOUND));

        // 2. BankAccount 생성
        BankAccount bankAccount =
                BankAccount.builder()
                        .institution(institution)
                        .accountNumber(request.accountNumber())
                        .ownerName(request.ownerName())
                        .balance(request.initialBalance())
                        .build();

        // 3. 저장
        BankAccount saved = bankAccountRepository.save(bankAccount);

        // 4. Response 반환
        return BankAccountResponse.from(saved);
    }
}
