package base.api.feature.posscan.controller;

import base.api.feature.posscan.dto.request.PushScanEventRequest;
import base.api.feature.posscan.dto.response.ScanEventFeedResponse;
import base.api.feature.posscan.service.IPosScanService;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pos/scan-events")
@Tag(name = "POS Scan Relay", description = "Điện thoại làm máy quét, đẩy mã sang máy bán hàng")
public class PosScanController extends BaseAPIController {

    @Autowired
    private IPosScanService posScanService;

    @Operation(
            summary = "Gửi mã vừa quét (thiết bị phụ)",
            description = "Điện thoại quét xong gọi API này. Mã được kiểm tra ngay như quét thường. "
                    + "Thành công hoặc lỗi (hết hàng / không tìm thấy) đều ghi vào hàng đợi; "
                    + "lỗi vẫn trả HTTP lỗi cho điện thoại, máy bán hàng poll được errorMessage."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @PostMapping
    public ResponseEntity<TFUResponse<ProductResponse>> push(
            @Valid @RequestBody PushScanEventRequest request) {
        ProductResponse data = posScanService.pushScanEvent(request);
        return success(data, "Đã gửi mã sang máy bán hàng.");
    }

    @Operation(
            summary = "Lấy mã mới (máy bán hàng)",
            description = "Máy bán hàng hỏi định kỳ. Gọi lần đầu KHÔNG truyền afterId để lấy con trỏ "
                    + "hiện tại; các lần sau truyền afterId = latestId của lần trước."
    )
    @PreAuthorize("@permissionChecker.has('CASHIER_ADD_POINTS')")
    @GetMapping
    public ResponseEntity<TFUResponse<ScanEventFeedResponse>> poll(
            @RequestParam(required = false) Long afterId) {
        return success(posScanService.pollScanEvents(afterId));
    }
}
