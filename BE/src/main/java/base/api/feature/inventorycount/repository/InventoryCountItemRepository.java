package base.api.feature.inventorycount.repository;

import base.api.shared.entity.InventoryCountItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventoryCountItemRepository extends JpaRepository<InventoryCountItemModel, Long> {

    List<InventoryCountItemModel> findBySessionId(Long sessionId);
}
