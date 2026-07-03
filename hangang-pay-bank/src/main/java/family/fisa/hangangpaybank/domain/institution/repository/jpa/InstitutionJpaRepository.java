package family.fisa.hangangpaybank.domain.institution.repository.jpa;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstitutionJpaRepository extends JpaRepository<Institution, Long> {

    Optional<Institution> findByInstitutionCode(String institutionCode);
}
