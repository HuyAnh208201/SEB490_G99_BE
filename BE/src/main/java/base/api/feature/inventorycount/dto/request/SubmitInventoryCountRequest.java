package base.api.feature.inventorycount.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload nộp phiên kiểm kê hàng hóa.
 */
@Getter
@Setter
public class SubmitInventoryCountRequest {

    private String note;

    @NotEmpty(message = "At least one counted item is required.")
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        @NotNull(message = "Product id is required.")
        private Integer productId;

        @NotNull(message = "Counted quantity is required.")
        @PositiveOrZero(message = "Counted quantity cannot be negative.")
        private Integer countedQty;

        private String note;
    }
}
