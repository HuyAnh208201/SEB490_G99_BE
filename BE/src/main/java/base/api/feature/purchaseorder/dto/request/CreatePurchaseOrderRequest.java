package base.api.feature.purchaseorder.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Supplier is required.")
    private Integer supplierId;

    private String notes;

    @NotEmpty(message = "At least one product must be added.")
    private List<Item> items;

    @Getter
    @Setter
    public static class Item {
        @NotNull(message = "Product is required.")
        private Integer productId;

        @NotNull(message = "Quantity is required.")
        private Integer quantity;

        private BigDecimal unitPrice;
    }
}
