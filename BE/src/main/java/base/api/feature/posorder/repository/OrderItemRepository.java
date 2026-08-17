package base.api.feature.posorder.repository;

import base.api.shared.entity.OrderItemModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItemModel, Long> {

    List<OrderItemModel> findByOrderIdIn(Collection<Long> orderIds);

    @Query("""
            SELECT oi.productId, COALESCE(SUM(oi.quantity), 0)
            FROM OrderItemModel oi, OrderModel o
            WHERE oi.orderId = o.id
              AND o.branchId = :branchId
              AND UPPER(o.status) = 'COMPLETED'
              AND o.createdAt >= :from
            GROUP BY oi.productId
            """)
    List<Object[]> sumSoldQuantityByProduct(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from);
}
