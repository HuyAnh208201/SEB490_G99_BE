package base.api.feature.branchreceiving.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Một dòng trong màn Order Tracking của nhân viên kho chi nhánh:
 * ứng với một yêu cầu nhập hàng đã được gom vào một lô vận chuyển.
 */
@Getter
@Setter
public class ReceivingOrderResponse {
    private Long dispatchOrderId;
    private String dispatchNumber;
    private Long requestId;
    private String requestNumber;
    private LocalDateTime shipmentDate;
    private LocalDateTime requestSubmittedAt;
    private LocalDate desiredReceiveDate;
    private String requestedByName;
    private String assignedReceiverName;
    private List<String> categories = new ArrayList<>();
    private Integer productCount;
    /** PREPARING / DELIVERING / RECEIVED */
    private String status;
    private boolean canReceive;
}
