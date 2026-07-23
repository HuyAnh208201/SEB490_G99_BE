package base.api.feature.posorder.controller;

import base.api.feature.posorder.dto.request.RefundRequestBody;
import base.api.feature.posorder.dto.request.RefundReviewBody;
import base.api.feature.posorder.dto.response.RefundResponse;
import base.api.feature.posorder.service.RefundService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/pos")
@Tag(name = "POS Refunds", description = "Hủy đơn / trả hàng: cashier yêu cầu trong 5 phút, BM duyệt")
public class RefundController extends BaseAPIController {

    @Autowired
    private RefundService refundService;

    @Operation(
            summary = "Cashier yêu cầu hoàn/trả một đơn",
            description = "Chỉ trong 5 phút sau khi tạo đơn, kèm lý do bắt buộc. Tạo yêu cầu "
                    + "chờ BM duyệt — cửa sổ thời gian được kiểm ở server."
    )
    @PreAuthorize("@permissionChecker.has('REFUND_REQUEST')")
    @PostMapping("/orders/{id}/refund-request")
    public ResponseEntity<TFUResponse<RefundResponse>> requestRefund(
            @PathVariable Long id,
            @Valid @RequestBody RefundRequestBody body) {
        return success(
                refundService.requestRefund(id, body.getReason()),
                "Refund request submitted for approval.");
    }

    @Operation(summary = "Danh sách yêu cầu hoàn/trả chờ duyệt (branch manager)")
    @PreAuthorize("@permissionChecker.has('REFUND_APPROVAL')")
    @GetMapping("/refunds/pending")
    public ResponseEntity<TFUResponse<List<RefundResponse>>> pending() {
        return success(refundService.getPendingRefunds());
    }

    @Operation(
            summary = "Duyệt yêu cầu hoàn/trả (branch manager)",
            description = "Hoàn tồn kho, thu hồi điểm và chuyển đơn sang REFUNDED."
    )
    @PreAuthorize("@permissionChecker.has('REFUND_APPROVAL')")
    @PostMapping("/refunds/{id}/approve")
    public ResponseEntity<TFUResponse<RefundResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RefundReviewBody body) {
        String note = body != null ? body.getNote() : null;
        return success(refundService.approveRefund(id, note), "Refund approved.");
    }

    @Operation(
            summary = "Từ chối yêu cầu hoàn/trả (branch manager)",
            description = "Ghi chú bắt buộc. Đơn giữ nguyên COMPLETED."
    )
    @PreAuthorize("@permissionChecker.has('REFUND_APPROVAL')")
    @PostMapping("/refunds/{id}/reject")
    public ResponseEntity<TFUResponse<RefundResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RefundReviewBody body) {
        String note = body != null ? body.getNote() : null;
        return success(refundService.rejectRefund(id, note), "Refund rejected.");
    }
}
