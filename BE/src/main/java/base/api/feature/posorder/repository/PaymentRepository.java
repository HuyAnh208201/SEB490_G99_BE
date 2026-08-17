package base.api.feature.posorder.repository;

import base.api.shared.entity.PaymentModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentModel, Long> {

    List<PaymentModel> findByOrderIdIn(Collection<Long> orderIds);

    /** Tìm payment theo orderId (cho PayOS payment link creation). */
    PaymentModel findByOrderId(Long orderId);

    /** Tìm payment theo orderCode payOS (lưu trong transaction_ref). */
    PaymentModel findByTransactionRef(String transactionRef);

    /**
     * Tổng tiền mặt thực thu trong một ca — vế "doanh thu tiền mặt" của công thức
     * Expected = opening fund + cash received − change given.
     *
     * Uses tendered cash minus change when those columns are present, falling
     * back to payment amount for older rows. Only CASH + SUCCESS + COMPLETED
     * orders count; refunds are subtracted later in refreshCashierTotals.
     */
    @Query("""
            SELECT COALESCE(SUM(COALESCE(p.cashReceived, p.amount) - COALESCE(p.changeAmount, 0)), 0) FROM PaymentModel p
            WHERE p.method = 'CASH'
              AND p.status = 'SUCCESS'
              AND p.orderId IN (SELECT o.id FROM OrderModel o
                                WHERE o.shiftId = :shiftId AND o.status = 'COMPLETED')
            """)
    BigDecimal sumCashTakenInShift(@Param("shiftId") Long shiftId);

    @Query("""
            SELECT COUNT(p) FROM PaymentModel p
            WHERE p.status = 'SUCCESS'
              AND p.orderId IN (SELECT o.id FROM OrderModel o
                                WHERE o.shiftId = :shiftId AND o.status = 'COMPLETED')
            """)
    long countTransactionsInShift(@Param("shiftId") Long shiftId);
}
