package base.api.feature.purchaseorder.repository;

import base.api.shared.entity.PurchaseOrderItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItemModel, Long> {

    List<PurchaseOrderItemModel> findByPurchaseOrderId(Long purchaseOrderId);

    List<PurchaseOrderItemModel> findByPurchaseOrderIdIn(Collection<Long> purchaseOrderIds);
}
