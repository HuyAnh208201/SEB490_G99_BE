package base.api.feature.posorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class RefundResponse {

    private Long refundId;
    private Long orderId;
    private String invoiceCode;
    private BigDecimal orderTotal;
    private String requestedByName;
    private String reason;
    private String status;
    private String reviewNote;
    private String reviewedByName;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
}
