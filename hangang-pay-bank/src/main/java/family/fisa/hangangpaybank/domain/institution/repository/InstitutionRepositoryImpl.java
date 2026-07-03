package family.fisa.hangangpaybank.domain.institution.repository;

import family.fisa.hangangpaybank.domain.institution.entity.Institution;
import family.fisa.hangangpaybank.domain.institution.repository.jpa.InstitutionJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class InstitutionRepositoryImpl implements InstitutionRepository {

    private final InstitutionJpaRepository jpaRepository;

    @Override
    public Institution save(Institution institution) {
        return jpaRepository.save(institution);
    }

    @Override
    public Optional<Institution> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Institution> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public long count() {
        return jpaRepository.count();
    }

    @Override
    public Optional<Institution> findByInstitutionCode(String institutionCode) {
        return jpaRepository.findByInstitutionCode(institutionCode);
    }
}
