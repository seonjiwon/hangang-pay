package family.fisa.hangangpaybank.domain.blockchain.repository;

import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import java.util.Optional;

/** contract 도메인 저장소 포트. 구현은 {@code ContractRepositoryImpl}. */
public interface ContractRepository {

    Contract save(Contract contract);

    Optional<Contract> findByInstitutionInstitutionCodeAndName(
            String institutionCode, ContractType name);

    Optional<Contract> findFirstByNameOrderByIdAsc(ContractType name);
}
