package base.api.feature.dispatch.service;

import base.api.feature.dispatch.dto.request.CreateDispatchOrderRequest;
import base.api.feature.dispatch.dto.request.UpdateDispatchStatusRequest;
import base.api.feature.dispatch.dto.response.DispatchApprovedRequestResponse;
import base.api.feature.dispatch.dto.response.DispatchOrderResponse;

import java.util.List;

public interface IDispatchService {

    /** Danh sách yêu cầu đã duyệt, sẵn sàng gom lô (màn Dispatch Planning). */
    List<DispatchApprovedRequestResponse> getApprovedRequests();

    /** Tạo một lô vận chuyển từ các yêu cầu đã duyệt được chọn. */
    DispatchOrderResponse createDispatchOrder(CreateDispatchOrderRequest request);

    /** Danh sách lô vận chuyển (mới nhất trước). */
    List<DispatchOrderResponse> getDispatchOrders();

    DispatchOrderResponse getDispatchOrder(Long id);

    /** Cập nhật trạng thái lô: PREPARING → DELIVERING → RECEIVED. */
    DispatchOrderResponse updateStatus(Long id, UpdateDispatchStatusRequest request);
}
