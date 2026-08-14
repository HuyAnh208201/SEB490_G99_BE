package base.api.feature.posorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OrderResponse {

    private Long id;
    private String invoiceCode;
    private Long branchId;
    private String branchName;
    private String branchAddress;
    private String branchPhone;
    private Long shiftId;
    private Long cashierId;
    private String cashierName;
    private Long customerId;
    private String customerName;
    private String customerPhone;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal total;
    private long pointsRedeemed;
    private long pointsEarned;
    private String status;
    private LocalDateTime createdAt;

    private String paymentMethod;
    private BigDecimal cashReceived;
    private BigDecimal changeAmount;
    private String paymentStatus;

    private Boolean refundable;

    private int itemCount;
    private List<OrderItemResponse> lines = new ArrayList<>();
}
