package base.api.feature.branch.repository;

import base.api.shared.entity.BranchSuspendTokenModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BranchSuspendTokenRepository extends JpaRepository<BranchSuspendTokenModel, Long> {

    Optional<BranchSuspendTokenModel> findTopByBranchIdAndUserIdAndUsedFalseOrderByCreatedAtDesc(
            Long branchId, Long userId);
}
