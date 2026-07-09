package base.api.feature.branch.repository;

import base.api.shared.entity.BranchModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IBranchRepository extends JpaRepository<BranchModel, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    Optional<BranchModel> findByManagerId(Long managerId);
}
