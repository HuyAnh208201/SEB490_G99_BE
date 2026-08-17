package base.api.feature.branchreceiving.service;

import base.api.feature.branchreceiving.dto.request.ReceiveShipmentRequest;
import base.api.feature.branchreceiving.dto.response.ReceiveShipmentDetailResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingHistoryResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingOrderResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingReceiptDetailResponse;
import base.api.feature.branchreceiving.dto.response.SupplementalRequestResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Nghiệp vụ nhập kho thực tế của nhân viên kho chi nhánh (Inventory Staff):
 * theo dõi lô vận chuyển, xác nhận nhập hàng thực tế và xem lịch sử nhập kho.
 */
public interface IBranchReceivingService {

    List<ReceivingOrderResponse> getIncomingOrders();

    Page<ReceivingOrderResponse> getIncomingOrderPage(PageRequestDTO pageRequest, String status);

    ReceiveShipmentDetailResponse getShipmentDetail(Long dispatchOrderId, Long requestId);

    ReceivingHistoryResponse receiveShipment(Long dispatchOrderId, Long requestId, ReceiveShipmentRequest request);

    List<ReceivingHistoryResponse> getReceivingHistory();

    Page<ReceivingHistoryResponse> getReceivingHistoryPage(PageRequestDTO pageRequest, String status);

    ReceivingReceiptDetailResponse getReceiptDetail(Long receiptId);

    SupplementalRequestResponse createSupplementalRequest(Long receiptId);

    ReceivingHistoryResponse approveReceipt(Long receiptId);

    ReceivingHistoryResponse rejectReceipt(Long receiptId);
}
