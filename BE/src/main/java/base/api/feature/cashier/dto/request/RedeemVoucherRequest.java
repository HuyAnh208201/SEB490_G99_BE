package base.api.feature.cashier.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Spends customer points on a voucher code at the counter. */
@Getter
@Setter
public class RedeemVoucherRequest {

    @NotBlank(message = "Customer phone is required.")
    @Size(max = 20, message = "Phone number is too long.")
    private String customerPhone;

    @NotNull(message = "Discount type is required.")
    private Long voucherCatalogId;
}
