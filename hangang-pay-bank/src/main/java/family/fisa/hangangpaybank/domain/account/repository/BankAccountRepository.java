package family.fisa.hangangpaybank.domain.account.repository;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import java.util.Optional;

/** bank_account 도메인 저장소 포트. 구현은 {@code BankAccountRepositoryImpl}. */
public interface BankAccountRepository {

    BankAccount save(BankAccount bankAccount);

    long count();

    Optional<BankAccount> findByInstitution_IdAndAccountNumber(
            Long institutionId, String accountNumber);

    Optional<BankAccount> findByInstitution_IdAndAccountNumberWithLock(
            Long institutionId, String accountNumber);
}
