package family.fisa.hangangpay.domain.institution.repository;

import family.fisa.hangangpay.domain.institution.entity.Institution;
import java.util.List;
import java.util.Optional;

public interface InstitutionRepository {

    Optional<Institution> findById(Long id);

    Optional<Institution> findByInstitutionCode(String institutionCode);

    List<Institution> findAllByOrderByIdAsc();
}
