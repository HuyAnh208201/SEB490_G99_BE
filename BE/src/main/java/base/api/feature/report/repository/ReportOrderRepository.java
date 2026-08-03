package base.api.feature.report.repository;

import base.api.feature.report.dto.InvoiceRow;
import base.api.feature.report.dto.OrderSummaryAgg;
import base.api.feature.report.dto.RevenueAggRow;
import base.api.feature.report.dto.TopProductAggRow;
import base.api.shared.entity.OrderModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Truy vấn tổng hợp phục vụ báo cáo trên bảng orders.
 *
 * Mọi query đều nhận optional branchId (null = toàn hệ thống) và khoảng thời gian
 * [from, to). Doanh thu chỉ tính đơn COMPLETED để loại đơn đã hoàn (REFUNDED).
 */
@Repository
public interface ReportOrderRepository extends JpaRepository<OrderModel, Long> {

    @Query("""
            SELECT new base.api.feature.report.dto.RevenueAggRow(
                o.shiftId, COUNT(o), SUM(o.total))
            FROM OrderModel o
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            GROUP BY o.shiftId
            ORDER BY o.shiftId
            """)
    List<RevenueAggRow> revenueByShift(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT new base.api.feature.report.dto.RevenueAggRow(
                o.cashierId, COUNT(o), SUM(o.total))
            FROM OrderModel o
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            GROUP BY o.cashierId
            ORDER BY o.cashierId
            """)
    List<RevenueAggRow> revenueByEmployee(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT new base.api.feature.report.dto.RevenueAggRow(
                o.branchId, COUNT(o), SUM(o.total))
            FROM OrderModel o
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            GROUP BY o.branchId
            ORDER BY o.branchId
            """)
    List<RevenueAggRow> revenueByBranch(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT new base.api.feature.report.dto.InvoiceRow(
                o.id, o.invoiceCode, o.branchId, o.cashierId, o.customerId,
                o.total, o.status, o.createdAt)
            FROM OrderModel o
            WHERE (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
              AND UPPER(o.status) IN ('COMPLETED', 'CANCELLED', 'REFUNDED')
            ORDER BY o.createdAt DESC
            """)
    List<InvoiceRow> findInvoices(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("""
            SELECT new base.api.feature.report.dto.OrderSummaryAgg(
                COUNT(o), SUM(o.total))
            FROM OrderModel o
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branchId = :branchId)
              AND (:shiftId IS NULL OR o.shiftId = :shiftId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            """)
    OrderSummaryAgg summarizeOrders(
            @Param("branchId") Long branchId,
            @Param("shiftId") Long shiftId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT DATE(o.created_at) AS day,
                   COALESCE(SUM(o.total), 0) AS revenue,
                   COUNT(o.id) AS order_count
            FROM orders o
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branch_id = :branchId)
              AND (:shiftId IS NULL OR o.shift_id = :shiftId)
              AND (:from IS NULL OR o.created_at >= :from)
              AND (:to IS NULL OR o.created_at < :to)
            GROUP BY DATE(o.created_at)
            ORDER BY day
            """, nativeQuery = true)
    List<Object[]> revenueTrendNative(
            @Param("branchId") Long branchId,
            @Param("shiftId") Long shiftId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
            SELECT new base.api.feature.report.dto.TopProductAggRow(
                i.productId, MAX(COALESCE(p.name, i.productName)), SUM(i.quantity), SUM(i.lineTotal))
            FROM OrderItemModel i, OrderModel o, ProductModel p
            WHERE i.orderId = o.id
              AND i.productId = p.id
              AND p.status = 'active'
              AND o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branchId = :branchId)
              AND (:shiftId IS NULL OR o.shiftId = :shiftId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
            GROUP BY i.productId
            ORDER BY SUM(i.lineTotal) DESC
            """)
    List<TopProductAggRow> topProductsByRevenue(
            @Param("branchId") Long branchId,
            @Param("shiftId") Long shiftId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query(value = """
            SELECT new base.api.feature.report.dto.InvoiceRow(
                o.id, o.invoiceCode, o.branchId, o.cashierId, o.customerId,
                o.total, o.status, o.createdAt)
            FROM OrderModel o
            WHERE (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
              AND UPPER(o.status) IN ('COMPLETED', 'CANCELLED', 'REFUNDED')
              AND (:search IS NULL OR LOWER(o.invoiceCode) LIKE :search OR LOWER(o.status) LIKE :search)
            ORDER BY o.createdAt DESC
            """, countQuery = """
            SELECT COUNT(o)
            FROM OrderModel o
            WHERE (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR o.createdAt >= :from)
              AND (:to IS NULL OR o.createdAt < :to)
              AND UPPER(o.status) IN ('COMPLETED', 'CANCELLED', 'REFUNDED')
              AND (:search IS NULL OR LOWER(o.invoiceCode) LIKE :search OR LOWER(o.status) LIKE :search)
            """)
    Page<InvoiceRow> findInvoicePage(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("search") String search,
            Pageable pageable);
}
