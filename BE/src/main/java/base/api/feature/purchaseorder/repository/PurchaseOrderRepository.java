package base.api.feature.purchaseorder.repository;

import base.api.shared.entity.PurchaseOrderModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderModel, Long> {

    List<PurchaseOrderModel> findAllByOrderByCreatedAtDesc();
}
