package base.api.feature.purchaserequest.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ReceiveGoodsRequest {

    /**
     * Số lượng thực nhận theo từng sản phẩm. Bỏ trống -> nhận đủ theo approved_quantity.
     */
    @Valid
    private List<ReceiveItem> items = new ArrayList<>();

    @Getter
    @Setter
    public static class ReceiveItem {

        @NotNull(message = "Product is required.")
        private Integer productId;

        @NotNull(message = "Received quantity is required.")
        @Min(value = 0, message = "Received quantity must be zero or greater.")
        private Integer receivedQuantity;
    }
}
