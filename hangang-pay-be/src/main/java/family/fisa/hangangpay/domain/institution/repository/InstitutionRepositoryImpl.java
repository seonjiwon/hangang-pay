package family.fisa.hangangpay.domain.institution.repository;

import family.fisa.hangangpay.domain.institution.entity.Institution;
import family.fisa.hangangpay.domain.institution.repository.jpa.InstitutionJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstitutionRepositoryImpl implements InstitutionRepository {

    private final InstitutionJpaRepository jpaRepository;

    @Override
    public Optional<Institution> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String institutionCode) {
        return jpaRepository.findByInstitutionCode(institutionCode);
    }

    @Override
    public List<Institution> findAllByOrderByIdAsc() {
        return jpaRepository.findAllByOrderByIdAsc();
    }
}
