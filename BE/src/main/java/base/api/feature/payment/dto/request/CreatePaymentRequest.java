package base.api.feature.payment.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * FE gửi sau khi checkout đơn với paymentMethod = PAYOS.
 */
@Data
public class CreatePaymentRequest {

    /** ID đơn hàng trong bảng orders. */
    @NotNull(message = "Order ID is required.")
    private Long orderId;
}
