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
 * Một yêu cầu hoàn/trả hàng cho đơn — cashier tạo, BM duyệt.
 *
 * Giữ trạng thái refund tách khỏi bảng orders để không nhồi thêm cột review vào
 * đơn; khi BM duyệt thì orders.status mới chuyển sang REFUNDED. Không đặt FK theo
 * lối dự án (xem PosOrderTablesMigration): customer/user/order đều tham chiếu mềm.
 */
@Getter
@Setter
@Entity
@Table(name = "order_refunds")
public class OrderRefundModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    /** users.id của cashier bấm yêu cầu. */
    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(length = 500)
    private String reason;

    /** PENDING / APPROVED / REJECTED. */
    @Column(nullable = false, length = 32)
    private String status = "PENDING";

    /** users.id của BM đã duyệt/từ chối, null khi còn chờ. */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "review_note", length = 500)
    private String reviewNote;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
}
