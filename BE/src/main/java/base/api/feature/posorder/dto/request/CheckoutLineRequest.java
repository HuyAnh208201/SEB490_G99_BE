package base.api.feature.posorder.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckoutLineRequest {

    @NotNull(message = "Product is required.")
    private Integer productId;

    @NotNull(message = "Quantity is required.")
    @Min(value = 1, message = "Quantity must be at least 1.")
    @Max(value = 10000, message = "Quantity is too large.")
    private Integer quantity;
}
