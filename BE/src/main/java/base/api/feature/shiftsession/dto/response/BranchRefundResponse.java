package base.api.feature.shiftsession.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
public class BranchRefundResponse {
    private Long refundId;
    private Long orderId;
    private String invoiceCode;
    private Long cashierId;
    private String cashierName;
    private BigDecimal amount;
    private LocalDateTime refundedAt;
    private String reason;
    private String status;
}
