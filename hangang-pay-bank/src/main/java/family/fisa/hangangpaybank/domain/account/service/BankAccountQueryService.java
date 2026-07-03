package family.fisa.hangangpaybank.domain.account.service;

import family.fisa.hangangpaybank.domain.account.code.AccountErrorCode;
import family.fisa.hangangpaybank.domain.account.dto.response.BankAccountResponse;
import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.account.repository.BankAccountRepository;
import family.fisa.hangangpaybank.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BankAccountQueryService {

    private final BankAccountRepository bankAccountRepository;

    public BankAccountResponse getByAccountNumberAndInstitutionId(
            String accountNumber, Long institutionId) {
        // 1. 기관 ID + 계좌번호로 원장 계좌 조회
        BankAccount bankAccount =
                bankAccountRepository
                        .findByInstitution_IdAndAccountNumber(institutionId, accountNumber)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                AccountErrorCode.BANK_ACCOUNT_NOT_FOUND));

        // 2. Response 반환
        return BankAccountResponse.from(bankAccount);
    }
}
