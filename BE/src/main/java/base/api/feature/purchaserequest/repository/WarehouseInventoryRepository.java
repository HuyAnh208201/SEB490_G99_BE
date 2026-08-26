package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.WarehouseInventoryModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseInventoryRepository extends JpaRepository<WarehouseInventoryModel, Long>, JpaSpecificationExecutor<WarehouseInventoryModel> {

    Optional<WarehouseInventoryModel> findByProductId(Integer productId);

    boolean existsByProductId(Integer productId);

    List<WarehouseInventoryModel> findByProductIdIn(Collection<Integer> productIds);

    @Query("""
            SELECT w FROM WarehouseInventoryModel w
            WHERE COALESCE(w.reorderPoint, 0) > 0
              AND COALESCE(w.quantity, 0) < COALESCE(w.reorderPoint, 0)
            """)
    List<WarehouseInventoryModel> findBelowReorderPoint();

    @Query("""
            SELECT COALESCE(SUM(w.quantity), 0) FROM WarehouseInventoryModel w
            """)
    long sumQuantity();

    @Query("""
            SELECT COUNT(w) FROM WarehouseInventoryModel w
            WHERE COALESCE(w.reorderPoint, 0) > 0
              AND COALESCE(w.quantity, 0) <= COALESCE(w.reorderPoint, 0)
            """)
    long countLowStock();
}
