package base.api.feature.branchreceiving.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Một dòng trong màn Receiving History (lịch sử nhập kho đã xác nhận).
 */
@Getter
@Setter
public class ReceivingHistoryResponse {
    private Long receiptId;
    private String receiptCode;
    private Long dispatchOrderId;
    private String dispatchNumber;
    private Long requestId;
    private String requestNumber;
    private LocalDateTime receivedAt;
    private Integer productCount;
    private String receivedByName;
    /** PENDING_APPROVAL / APPROVED / REJECTED */
    private String status;
}
