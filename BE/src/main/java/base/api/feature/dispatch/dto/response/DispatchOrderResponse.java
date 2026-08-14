package base.api.feature.dispatch.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Lô vận chuyển — dùng cho cả danh sách (Dispatch Orders) và chi tiết.
 */
@Getter
@Setter
public class DispatchOrderResponse {
    private Long id;
    private String dispatchNumber;
    private String status;
    private String deliveryArea;
    private String route;
    private LocalDateTime createdAt;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;
    private Long senderId;
    private String senderName;
    private String senderPhone;
    private Long recipientId;
    private String recipientName;
    private String recipientPhone;
    private List<Integer> supplierIds = new ArrayList<>();
    private List<String> supplierNames = new ArrayList<>();
    private List<RequestLine> requests = new ArrayList<>();

    @Getter
    @Setter
    public static class RequestLine {
        private Long requestId;
        private String requestNumber;
        private Long branchId;
        private String branchName;
        private Integer itemCount;
        private LocalDateTime requestSubmittedAt;
        private LocalDate desiredReceiveDate;
        private String requestedByName;
        private LocalDateTime receivedAt;
        private String receivedByName;
        private List<ItemLine> items = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ItemLine {
        private Integer productId;
        private String productCode;
        private String productName;
        private String unit;
        private String categoryName;
        private BigDecimal unitCost;
        /** Quantity expressed in TOP packaging units (see topPackagingLabel), not base stock units. */
        private Integer quantity;
        private Integer actualReceivedQuantity;
        private Integer difference;
        /** English label of the TOP packaging level, e.g. "Case of 24". */
        private String topPackagingLabel;
    }
}
