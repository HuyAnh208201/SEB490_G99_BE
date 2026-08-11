package base.api.feature.voucher.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Tạo hoặc sửa một loại voucher (PERCENT hay FIXED). */
@Getter
@Setter
public class SaveVoucherCatalogRequest {

    @NotBlank(message = "Discount type name is required.")
    @Size(max = 255, message = "Discount type name must not exceed 255 characters.")
    private String name;

    /** PERCENT hoặc FIXED. */
    @NotBlank(message = "Discount type is required.")
    private String discountType;

    /** PERCENT thì là số phần trăm (0-100), FIXED thì là số tiền VNĐ. */
    @NotNull(message = "Discount value is required.")
    @DecimalMin(value = "0", message = "Discount value must be greater than or equal to 0.")
    private BigDecimal discountValue;

    /** Số điểm khách phải đổi để lấy mã; 0 nghĩa là không phát qua đổi điểm. */
    @Min(value = 0, message = "Points required must be greater than or equal to 0.")
    private Integer pointsRequired;

    /** active hoặc inactive. Bỏ trống khi tạo thì mặc định active. */
    private String status;
}
