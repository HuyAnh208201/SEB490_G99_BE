package base.api.feature.branchreceiving.dto.response;

import lombok.Getter;
import lombok.Setter;

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
        private Integer orderedQuantity;
        private Integer receivedQuantity;
        private String note;
    }
}
