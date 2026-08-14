package base.api.feature.purchaseorder.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.time.LocalDate;
import jakarta.validation.constraints.NotBlank;

@Getter
@Setter
public class CreatePurchaseOrderRequest {

    @NotNull(message = "Supplier is required.")
    private Integer supplierId;

    private String notes;

    @NotNull(message = "Supplier delivery date is required.")
    private LocalDate supplierDeliveryDate;

    @NotBlank(message = "Delivery person name is required.")
    private String deliveredByName;

    private String deliveredByPhone;

    private String supplierDocumentNumber;

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
