package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import family.fisa.hangangpaybank.domain.blockchain.repository.jpa.ContractJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ContractRepositoryImpl implements ContractRepository {

    private final ContractJpaRepository jpaRepository;

    @Override
    public Contract save(Contract contract) {
        return jpaRepository.save(contract);
    }

    @Override
    public Optional<Contract> findByInstitutionInstitutionCodeAndName(
            String institutionCode, ContractType name) {
        return jpaRepository.findByInstitutionInstitutionCodeAndName(institutionCode, name);
    }

    @Override
    public Optional<Contract> findFirstByNameOrderByIdAsc(ContractType name) {
        return jpaRepository.findFirstByNameOrderByIdAsc(name);
    }
}
