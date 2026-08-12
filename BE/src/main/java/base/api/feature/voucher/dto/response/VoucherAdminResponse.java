package base.api.feature.voucher.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One issued code, carrying its voucher type so the admin screen need not join by hand. */
@Getter
@Setter
public class VoucherAdminResponse {

    private Long id;
    private String code;
    private Long voucherCatalogId;
    private String catalogName;
    /** PERCENT or FIXED. */
    private String discountType;
    private BigDecimal discountValue;
    /** users.id of the customer it is reserved for; null means a shared code. */
    private Long customerId;
    private String customerName;
    private String customerPhone;
    /** active or used. */
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}
