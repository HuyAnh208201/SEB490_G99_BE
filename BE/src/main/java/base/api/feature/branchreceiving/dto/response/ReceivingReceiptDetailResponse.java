package base.api.feature.branchreceiving.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Chi tiết một phiếu nhập kho (goods receipt) cho màn Receiving History.
 */
@Getter
@Setter
public class ReceivingReceiptDetailResponse {
    private Long receiptId;
    private String receiptCode;
    private String dispatchNumber;
    private String requestNumber;
    private LocalDateTime receivedAt;
    private String receivedByName;
    private String receivedByPhone;
    private String senderName;
    private String senderPhone;
    private String assignedReceiverName;
    private String assignedReceiverPhone;
    private LocalDateTime shipmentDate;
    private LocalDateTime requestSubmittedAt;
    private LocalDate desiredReceiveDate;
    private String requestedByName;
    private String storeName;
    private String status;
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
        private Integer orderedQuantity;
        private Integer receivedQuantity;
        private Integer difference;
        private String note;
    }
}
