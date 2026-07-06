package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.BranchInventoryModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchInventoryRepository extends JpaRepository<BranchInventoryModel, Long> {

    List<BranchInventoryModel> findByBranchId(Long branchId);

    Optional<BranchInventoryModel> findByBranchIdAndProductId(Long branchId, Integer productId);
}
