package base.api.feature.voucher.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một mã đã phát, kèm thông tin loại voucher để màn quản lý không phải join tay. */
@Getter
@Setter
public class VoucherAdminResponse {

    private Long id;
    private String code;
    private Long voucherCatalogId;
    private String catalogName;
    /** PERCENT hoặc FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    /** users.id của khách được phát riêng; null nghĩa là mã dùng chung. */
    private Long customerId;
    private String customerName;
    private String customerPhone;
    /** active hoặc used. */
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
