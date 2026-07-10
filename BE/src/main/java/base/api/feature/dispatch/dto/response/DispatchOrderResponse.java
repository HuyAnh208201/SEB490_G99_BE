package base.api.feature.dispatch.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
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
    private String vehicle;
    private String deliveryArea;
    private String route;
    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private List<RequestLine> requests = new ArrayList<>();

    @Getter
    @Setter
    public static class RequestLine {
        private Long requestId;
        private String requestNumber;
        private Long branchId;
        private String branchName;
        private Integer itemCount;
        private List<ItemLine> items = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ItemLine {
        private Integer productId;
        private String productCode;
        private String productName;
        private String unit;
        private Integer quantity;
    }
}
