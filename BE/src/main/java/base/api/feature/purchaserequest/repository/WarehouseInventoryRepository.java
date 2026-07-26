package base.api.feature.purchaserequest.repository;

import base.api.shared.entity.WarehouseInventoryModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseInventoryRepository extends JpaRepository<WarehouseInventoryModel, Long>, JpaSpecificationExecutor<WarehouseInventoryModel> {

    Optional<WarehouseInventoryModel> findByProductId(Integer productId);

    List<WarehouseInventoryModel> findByProductIdIn(Collection<Integer> productIds);
}
