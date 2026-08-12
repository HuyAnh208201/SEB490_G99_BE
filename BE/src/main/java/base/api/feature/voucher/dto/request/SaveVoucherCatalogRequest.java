package base.api.feature.voucher.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** Creates or edits a voucher type (PERCENT or FIXED). */
@Getter
@Setter
public class SaveVoucherCatalogRequest {

    @NotBlank(message = "Discount type name is required.")
    @Size(max = 255, message = "Discount type name must not exceed 255 characters.")
    private String name;

    /** PERCENT or FIXED. */
    @NotBlank(message = "Discount type is required.")
    private String discountType;

    /** A percentage (0-100) for PERCENT, an amount in VND for FIXED. */
    @NotNull(message = "Discount value is required.")
    @DecimalMin(value = "0", message = "Discount value must be greater than or equal to 0.")
    private BigDecimal discountValue;

    /** Points a customer pays for the code; 0 means it is not sold for points. */
    @Min(value = 0, message = "Points required must be greater than or equal to 0.")
    private Integer pointsRequired;

    /** active or inactive. Empty on create defaults to active. */
    private String status;
}
