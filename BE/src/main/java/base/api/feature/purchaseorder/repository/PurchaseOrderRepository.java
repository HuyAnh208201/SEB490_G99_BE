package base.api.feature.purchaseorder.repository;

import base.api.shared.entity.PurchaseOrderModel;
import base.api.shared.enums.PurchaseOrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrderModel, Long>, JpaSpecificationExecutor<PurchaseOrderModel> {

    List<PurchaseOrderModel> findAllByOrderByCreatedAtDesc();

    long countByStatus(PurchaseOrderStatus status);
}
