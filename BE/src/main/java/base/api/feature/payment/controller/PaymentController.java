package base.api.feature.payment.controller;

import base.api.feature.payment.dto.request.CreatePaymentRequest;
import base.api.feature.payment.dto.response.PaymentLinkResponse;
import base.api.feature.payment.dto.response.PaymentStatusResponse;
import base.api.feature.payment.service.IPaymentService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
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

/**
 * API thanh toán PayOS cho POS.
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController extends BaseAPIController {

    @Autowired
    private IPaymentService paymentService;

    /**
     * Tạo payment link + QR cho một đơn hàng đã tồn tại.
     * Cashier gọi sau khi checkout với paymentMethod = PAYOS.
     */
    @PostMapping("/create-link")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    public ResponseEntity<TFUResponse<PaymentLinkResponse>> createPaymentLink(
            @Valid @RequestBody CreatePaymentRequest request) {
        return success(paymentService.createPaymentLink(request), "Payment link created.");
    }

    /**
     * FE polling: kiểm tra trạng thái thanh toán (backup cho webhook).
     */
    @GetMapping("/status/{orderCode}")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    public ResponseEntity<TFUResponse<PaymentStatusResponse>> getPaymentStatus(
            @PathVariable long orderCode) {
        return success(paymentService.getPaymentStatus(orderCode));
    }

    /**
     * Hủy payment link nếu khách không thanh toán.
     */
    @PostMapping("/cancel/{orderCode}")
    @PreAuthorize("@permissionChecker.has('POS_CHECKOUT')")
    public ResponseEntity<TFUResponse<Void>> cancelPaymentLink(@PathVariable long orderCode) {
        paymentService.cancelPaymentLink(orderCode);
        return success(null, "Payment link cancelled.");
    }
}
