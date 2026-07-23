package base.api.feature.report.repository;

import base.api.feature.report.dto.PointTransactionRow;
import base.api.shared.entity.PointTransactionModel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Lưu + truy vấn lịch sử tích/đổi điểm.
 *
 * Bảng point_transactions không có branch_id, nên lọc theo chi nhánh phải join
 * sang orders qua order_id. Dùng LEFT JOIN để khi branchId null (toàn hệ thống) vẫn
 * lấy được cả giao dịch không gắn đơn (order_id null, vd tích điểm rời qua /add-points);
 * khi lọc theo branchId cụ thể thì các giao dịch không gắn đơn tự bị loại vì o là null.
 */
@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransactionModel, Long> {

    @Query("""
            SELECT new base.api.feature.report.dto.PointTransactionRow(
                pt.id, pt.customerId, pt.orderId, pt.points, pt.type, pt.createdAt)
            FROM PointTransactionModel pt
            LEFT JOIN OrderModel o ON o.id = pt.orderId
            WHERE (:branchId IS NULL OR o.branchId = :branchId)
              AND (:from IS NULL OR pt.createdAt >= :from)
              AND (:to IS NULL OR pt.createdAt < :to)
            ORDER BY pt.createdAt DESC
            """)
    List<PointTransactionRow> findHistory(
            @Param("branchId") Long branchId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);
}
