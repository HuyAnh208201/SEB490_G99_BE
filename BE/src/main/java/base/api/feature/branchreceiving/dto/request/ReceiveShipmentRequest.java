package base.api.feature.branchreceiving.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Payload xác nhận nhập kho thực tế cho một yêu cầu trong lô vận chuyển.
 */
@Getter
@Setter
public class ReceiveShipmentRequest {

    @NotEmpty(message = "At least one item is required.")
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        @NotNull(message = "Product id is required.")
        private Integer productId;

        @NotNull(message = "Received quantity is required.")
        @PositiveOrZero(message = "Received quantity cannot be negative.")
        private Integer receivedQuantity;

        private String note;
    }
}
