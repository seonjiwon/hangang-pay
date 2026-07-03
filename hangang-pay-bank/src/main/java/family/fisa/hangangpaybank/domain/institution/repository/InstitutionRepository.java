package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import java.util.List;
import java.util.Optional;

/** institution 도메인 저장소 포트. 구현은 {@code InstitutionRepositoryImpl}. */
public interface InstitutionRepository {

    Institution save(Institution institution);

    Optional<Institution> findById(Long id);

    List<Institution> findAll();

    long count();

    Optional<Institution> findByInstitutionCode(String institutionCode);
}
