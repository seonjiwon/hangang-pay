package family.fisa.hangangpaybank.domain.blockchain.repository.jpa;

import family.fisa.hangangpaybank.domain.blockchain.entity.Contract;
import family.fisa.hangangpaybank.domain.blockchain.entity.ContractType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContractJpaRepository extends JpaRepository<Contract, Long> {

    Optional<Contract> findByInstitutionInstitutionCodeAndName(
            String institutionCode, ContractType name);

    @EntityGraph(attributePaths = "institution")
    Optional<Contract> findFirstByNameOrderByIdAsc(ContractType name);
}
