package base.api.feature.branchreceiving.dto.response;

import lombok.Getter;
import lombok.Setter;

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
        private Integer shippedQuantity;
    }
}
