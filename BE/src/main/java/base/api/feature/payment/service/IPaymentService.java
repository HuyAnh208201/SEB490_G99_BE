package base.api.feature.payment.service;

import base.api.feature.payment.dto.request.CreatePaymentRequest;
import base.api.feature.payment.dto.response.PaymentLinkResponse;
import base.api.feature.payment.dto.response.PaymentStatusResponse;

import java.util.Map;

public interface IPaymentService {

    /** Tạo payment link + QR cho đơn hàng. */
    PaymentLinkResponse createPaymentLink(CreatePaymentRequest request);

    /** Kiểm tra trạng thái thanh toán (FE polling backup). */
    PaymentStatusResponse getPaymentStatus(long orderCode);

    /** Hủy payment link. */
    void cancelPaymentLink(long orderCode);

    /** Xử lý webhook từ payOS. */
    void handleWebhook(Map<String, Object> body);
}
