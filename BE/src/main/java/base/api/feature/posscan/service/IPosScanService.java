package base.api.feature.posscan.service;

import base.api.feature.posscan.dto.request.PushScanEventRequest;
import base.api.feature.posscan.dto.response.ScanEventFeedResponse;
import base.api.feature.product.dto.response.ProductResponse;

public interface IPosScanService {

    /**
     * Thiết bị phụ (điện thoại) gửi mã vừa quét lên.
     * Mã được kiểm tra ngay bằng luồng quét chuẩn nên điện thoại biết liền
     * là hợp lệ hay hết hàng. Lỗi vẫn được ghi vào hàng đợi (errorMessage)
     * để máy bán hàng poll được, rồi exception được ném lại cho điện thoại.
     */
    ProductResponse pushScanEvent(PushScanEventRequest request);

    /**
     * Máy bán hàng hỏi các mã mới hơn afterId.
     * Truyền afterId = null để lấy con trỏ hiện tại mà không nuốt lại mã cũ.
     */
    ScanEventFeedResponse pollScanEvents(Long afterId);
}
