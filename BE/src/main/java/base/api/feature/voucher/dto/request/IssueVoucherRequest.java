package base.api.feature.voucher.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Phát mã giảm giá: một mã cho khách cụ thể, hoặc một lô mã dùng chung. */
@Getter
@Setter
public class IssueVoucherRequest {

    @NotNull(message = "Discount type is required.")
    private Long voucherCatalogId;

    /**
     * ID người dùng (users.id) của khách được phát riêng. Bỏ trống nghĩa là mã dùng
     * chung, ai cũng áp được.
     */
    private Long customerId;

    /** Tiền tố mã cho dễ đọc tại quầy; bỏ trống thì dùng mặc định. */
    @Size(max = 16, message = "Code prefix must not exceed 16 characters.")
    private String codePrefix;

    /** Số mã cần sinh. Mã phát riêng cho khách chỉ được sinh 1. */
    @Min(value = 1, message = "Quantity must be at least 1.")
    @Max(value = 200, message = "Cannot issue more than 200 codes at once.")
    private Integer quantity;

    @NotNull(message = "Expiry date is required.")
    private LocalDateTime expiresAt;
}
