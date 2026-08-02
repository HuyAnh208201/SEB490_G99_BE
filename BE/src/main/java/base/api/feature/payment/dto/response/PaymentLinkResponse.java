package base.api.feature.payment.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * Trả về cho FE sau khi tạo payment link thành công.
 */
@Data
@Builder
public class PaymentLinkResponse {
    private Long orderId;
    private long orderCode;
    private long amount;
    private String checkoutUrl;
    private String qrCode;
    private String status;
}
