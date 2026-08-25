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
 * Một lần quét mã từ thiết bị phụ (điện thoại) gửi về cho máy bán hàng.
 * Máy bán hàng đọc theo con trỏ id tăng dần nên không sợ lệch đồng hồ giữa 2 máy.
 * Sự kiện lỗi (hết hàng / không tìm thấy) vẫn được lưu để máy bán hàng hiển thị.
 */
@Getter
@Setter
@Entity
@Table(name = "pos_scan_events")
public class PosScanEventModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Thu ngân sở hữu phiên bán hàng — dùng để ghép điện thoại với máy bán hàng. */
    @Column(name = "cashier_user_id", nullable = false)
    private Long cashierUserId;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "barcode", nullable = false)
    private String barcode;

    /** Lưu kèm để máy bán hàng hiển thị ngay, và để tra lại khi cần. Null khi quét lỗi. */
    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "product_name")
    private String productName;

    /** Lý do quét thất bại (hết hàng, không tìm thấy, …). Null khi thành công. */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
