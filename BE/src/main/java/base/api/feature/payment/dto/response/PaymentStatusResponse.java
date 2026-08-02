package base.api.feature.payment.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Trả về trạng thái thanh toán khi FE polling.
 */
@Data
@Builder
public class PaymentStatusResponse {
    private long orderCode;
    private Long orderId;
    private String status;
    private String transactionRef;
}
