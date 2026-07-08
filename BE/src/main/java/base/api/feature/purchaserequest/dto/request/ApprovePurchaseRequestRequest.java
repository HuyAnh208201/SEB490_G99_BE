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
public class ApprovePurchaseRequestRequest {

    /**
     * Số lượng duyệt theo từng sản phẩm. Bỏ trống -> duyệt full theo requested_quantity.
     */
    @Valid
    private List<ApproveItem> items = new ArrayList<>();

    @Getter
    @Setter
    public static class ApproveItem {

        @NotNull(message = "Product is required.")
        private Integer productId;

        @NotNull(message = "Approved quantity is required.")
        @Min(value = 0, message = "Approved quantity must be zero or greater.")
        private Integer approvedQuantity;
    }
}
