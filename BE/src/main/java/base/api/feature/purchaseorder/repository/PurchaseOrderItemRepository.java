package base.api.feature.purchaseorder.repository;

import base.api.shared.entity.PurchaseOrderItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface PurchaseOrderItemRepository extends JpaRepository<PurchaseOrderItemModel, Long> {

    List<PurchaseOrderItemModel> findByPurchaseOrderId(Long purchaseOrderId);

    List<PurchaseOrderItemModel> findByPurchaseOrderIdIn(Collection<Long> purchaseOrderIds);

    @Query(value = """
            SELECT poi.unit_price
            FROM purchase_order_items poi
            INNER JOIN purchase_orders po ON po.id = poi.purchase_order_id
            WHERE poi.product_id = :productId
              AND po.status = 'RECEIVED'
            ORDER BY po.received_at DESC, po.id DESC
            LIMIT 1
            """, nativeQuery = true)
    BigDecimal findLatestReceivedUnitPrice(@Param("productId") Integer productId);
}
