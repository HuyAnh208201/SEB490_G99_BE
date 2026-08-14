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

import java.math.BigDecimal;
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
            SELECT COALESCE(SUM(
                i.quantity * COALESCE(
                    i.unit_cost,
                    (
                        SELECT poi.unit_price / GREATEST(COALESCE(
                            (SELECT pp.conversion_qty FROM product_packagings pp
                             WHERE pp.product_id = i.product_id AND pp.is_purchase_default = 1
                             LIMIT 1),
                            NULLIF(p.units_per_import_unit, 0),
                            1), 1)
                        FROM purchase_order_items poi
                        INNER JOIN purchase_orders po ON po.id = poi.purchase_order_id
                        WHERE poi.product_id = i.product_id AND po.status = 'RECEIVED'
                        ORDER BY po.received_at DESC, po.id DESC
                        LIMIT 1
                    ),
                    p.reference_import_price,
                    0)
            ), 0)
            FROM order_items i
            INNER JOIN orders o ON o.id = i.order_id
            INNER JOIN products p ON p.id = i.product_id
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branch_id = :branchId)
              AND (:shiftId IS NULL OR o.shift_id = :shiftId)
              AND (:from IS NULL OR o.created_at >= :from)
              AND (:to IS NULL OR o.created_at < :to)
            """, nativeQuery = true)
    BigDecimal sumCompletedCogs(
            @Param("branchId") Long branchId,
            @Param("shiftId") Long shiftId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT o.branch_id AS group_id,
                   COALESCE(SUM(
                       i.quantity * COALESCE(i.unit_cost, p.reference_import_price, 0)
                   ), 0) AS cogs
            FROM orders o
            INNER JOIN order_items i ON i.order_id = o.id
            INNER JOIN products p ON p.id = i.product_id
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branch_id = :branchId)
              AND (:from IS NULL OR o.created_at >= :from)
              AND (:to IS NULL OR o.created_at < :to)
            GROUP BY o.branch_id
            """, nativeQuery = true)
    List<Object[]> cogsByBranchNative(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT o.shift_id AS group_id,
                   COALESCE(SUM(
                       i.quantity * COALESCE(i.unit_cost, p.reference_import_price, 0)
                   ), 0) AS cogs
            FROM orders o
            INNER JOIN order_items i ON i.order_id = o.id
            INNER JOIN products p ON p.id = i.product_id
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branch_id = :branchId)
              AND (:from IS NULL OR o.created_at >= :from)
              AND (:to IS NULL OR o.created_at < :to)
            GROUP BY o.shift_id
            """, nativeQuery = true)
    List<Object[]> cogsByShiftNative(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT o.cashier_id AS group_id,
                   COALESCE(SUM(
                       i.quantity * COALESCE(i.unit_cost, p.reference_import_price, 0)
                   ), 0) AS cogs
            FROM orders o
            INNER JOIN order_items i ON i.order_id = o.id
            INNER JOIN products p ON p.id = i.product_id
            WHERE o.status = 'COMPLETED'
              AND (:branchId IS NULL OR o.branch_id = :branchId)
              AND (:from IS NULL OR o.created_at >= :from)
              AND (:to IS NULL OR o.created_at < :to)
            GROUP BY o.cashier_id
            """, nativeQuery = true)
    List<Object[]> cogsByEmployeeNative(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query(value = """
            SELECT DATE(o.created_at) AS day,
                   COALESCE(SUM(o.total), 0) AS revenue,
                   COUNT(o.id) AS order_count,
                   COALESCE((
                       SELECT SUM(i.quantity * COALESCE(i.unit_cost, p.reference_import_price, 0))
                       FROM order_items i
                       INNER JOIN products p ON p.id = i.product_id
                       WHERE i.order_id IN (
                           SELECT o2.id FROM orders o2
                           WHERE o2.status = 'COMPLETED'
                             AND DATE(o2.created_at) = DATE(o.created_at)
                             AND (:branchId IS NULL OR o2.branch_id = :branchId)
                             AND (:shiftId IS NULL OR o2.shift_id = :shiftId)
                       )
                   ), 0) AS cogs
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
                i.productId, MAX(COALESCE(p.name, i.productName)), SUM(i.quantity), SUM(i.lineTotal),
                SUM(i.quantity * COALESCE(i.unitCost, p.referenceImportPrice, 0)))
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
