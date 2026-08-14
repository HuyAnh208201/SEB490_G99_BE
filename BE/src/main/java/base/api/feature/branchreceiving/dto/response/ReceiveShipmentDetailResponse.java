package base.api.feature.branchreceiving.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu cho màn Receive Shipment (nhập kho thực tế): thông tin lô + danh sách mặt hàng cần xác nhận.
 */
@Getter
@Setter
public class ReceiveShipmentDetailResponse {
    private Long dispatchOrderId;
    private String dispatchNumber;
    private Long requestId;
    private String requestNumber;
    private LocalDateTime shipmentDate;
    private LocalDateTime requestSubmittedAt;
    private LocalDate desiredReceiveDate;
    private String requestedByName;
    private String senderName;
    private String senderPhone;
    private String assignedReceiverName;
    private String assignedReceiverPhone;
    private Long branchId;
    private String storeName;
    private String source;
    private String status;
    private boolean canReceive;
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        private Integer productId;
        private String productCode;
        private String productName;
        private String unit;
        private String categoryName;
        private BigDecimal unitCost;
        private Integer shippedQuantity;
    }
}
