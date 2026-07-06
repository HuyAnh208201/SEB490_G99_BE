package base.api.feature.purchaserequest.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PurchaseRequestItemRequest {

    @NotNull(message = "Product is required.")
    private Integer productId;

    @NotNull(message = "Quantity is required.")
    @DecimalMin(value = "1", message = "Quantity must be greater than zero.")
    @Digits(integer = 10, fraction = 0, message = "Quantity must be an integer.")
    private BigDecimal requestedQty;
}
