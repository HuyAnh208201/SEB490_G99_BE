package base.api.shared.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Một dòng lịch sử tích/đổi điểm của khách hàng.
 *
 * Điểm thực tế vẫn cộng/trừ trực tiếp trên {@code users.points} (atomic). Bảng này
 * CHỈ lưu lịch sử để báo cáo — không phải nguồn dữ liệu điểm hiện tại. Theo dump SQL
 * gốc bảng không có branch_id: report theo chi nhánh phải join order_id → orders.branch_id,
 * nên các giao dịch không gắn đơn (order_id null) không thuộc chi nhánh nào. Không đặt FK
 * theo cùng lối với PosOrderTablesMigration (tham chiếu mềm).
 */
@Getter
@Setter
@Entity
@Table(name = "point_transactions")
public class PointTransactionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** users.id (role CUSTOMER) của khách được cộng/trừ điểm. */
    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    /** orders.id nếu giao dịch phát sinh từ một đơn; null nếu tích điểm rời (không qua đơn). */
    @Column(name = "order_id")
    private Long orderId;

    /** Dương = cộng điểm, âm = trừ điểm. */
    @Column(nullable = false)
    private Long points;

    /** EARN / REDEEM / REFUND_REVERSAL. */
    @Column(nullable = false, length = 32)
    private String type;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
