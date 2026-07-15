package base.api.feature.inventorycount.repository;

import base.api.shared.entity.InventoryCountSessionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryCountSessionRepository extends JpaRepository<InventoryCountSessionModel, Long> {

    List<InventoryCountSessionModel> findByBranchIdOrderByCreatedAtDesc(Long branchId);
}
