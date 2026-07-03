package family.fisa.hangangpaybank.domain.account.repository;

import family.fisa.hangangpaybank.domain.account.entity.BankAccount;
import family.fisa.hangangpaybank.domain.account.repository.jpa.BankAccountJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class BankAccountRepositoryImpl implements BankAccountRepository {

    private final BankAccountJpaRepository jpaRepository;

    @Override
    public BankAccount save(BankAccount bankAccount) {
        return jpaRepository.save(bankAccount);
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public Optional<BankAccount> findByInstitution_IdAndAccountNumber(
            Long institutionId, String accountNumber) {
        return jpaRepository.findByInstitution_IdAndAccountNumber(institutionId, accountNumber);
    }

    @Override
    public Optional<BankAccount> findByInstitution_IdAndAccountNumberWithLock(
            Long institutionId, String accountNumber) {
        return jpaRepository.findByInstitution_IdAndAccountNumberWithLock(
                institutionId, accountNumber);
    }
}
