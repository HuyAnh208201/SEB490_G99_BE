package base.api.feature.branchreceiving.service;

import base.api.feature.branchreceiving.dto.request.ReceiveShipmentRequest;
import base.api.feature.branchreceiving.dto.response.ReceiveShipmentDetailResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingHistoryResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingOrderResponse;
import base.api.feature.branchreceiving.dto.response.ReceivingReceiptDetailResponse;

import java.util.List;

/**
 * Nghiệp vụ nhập kho thực tế của nhân viên kho chi nhánh (Inventory Staff):
 * theo dõi lô vận chuyển, xác nhận nhập hàng thực tế và xem lịch sử nhập kho.
 */
public interface IBranchReceivingService {

    List<ReceivingOrderResponse> getIncomingOrders();

    ReceiveShipmentDetailResponse getShipmentDetail(Long dispatchOrderId, Long requestId);

    ReceivingHistoryResponse receiveShipment(Long dispatchOrderId, Long requestId, ReceiveShipmentRequest request);

    List<ReceivingHistoryResponse> getReceivingHistory();

    ReceivingReceiptDetailResponse getReceiptDetail(Long receiptId);
}
