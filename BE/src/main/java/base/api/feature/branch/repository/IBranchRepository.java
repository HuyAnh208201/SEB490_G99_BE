package base.api.feature.branch.repository;

import base.api.shared.entity.BranchModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface IBranchRepository extends JpaRepository<BranchModel, Long>, JpaSpecificationExecutor<BranchModel> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByStatusIgnoreCase(String status);
}
