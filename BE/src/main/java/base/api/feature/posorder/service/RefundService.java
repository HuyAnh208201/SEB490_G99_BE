package base.api.feature.posorder.service;

import base.api.feature.posorder.dto.response.RefundResponse;

import java.util.List;

/**
 * Hủy đơn / trả hàng cần BM duyệt.
 *
 * Cashier yêu cầu hoàn/trả trong 5 phút sau khi tạo đơn kèm lý do; BM duyệt thì
 * hoàn tồn kho, thu hồi điểm và chuyển đơn sang REFUNDED (loại khỏi doanh thu).
 */
public interface RefundService {

    /** Cashier yêu cầu hoàn/trả một đơn (chỉ trong cửa sổ 5 phút). */
    RefundResponse requestRefund(Long orderId, String reason);

    /** Danh sách yêu cầu hoàn/trả đang chờ duyệt của chi nhánh BM. */
    List<RefundResponse> getPendingRefunds();

    /** BM duyệt: hoàn tồn kho, thu hồi điểm, đơn chuyển REFUNDED. */
    RefundResponse approveRefund(Long refundId, String note);

    /** BM từ chối: đơn giữ nguyên COMPLETED. */
    RefundResponse rejectRefund(Long refundId, String note);
}
