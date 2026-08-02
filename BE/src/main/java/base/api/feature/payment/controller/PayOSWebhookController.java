package base.api.feature.payment.controller;

import base.api.feature.payment.service.IPaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Webhook endpoint nhận callback từ payOS khi khách thanh toán xong.
 * Endpoint này KHÔNG yêu cầu JWT — payOS gọi trực tiếp.
 * Bảo mật bằng verify signature trong body webhook.
 */
@RestController
@RequestMapping("/api/payment")
public class PayOSWebhookController {

    private static final Logger log = LoggerFactory.getLogger(PayOSWebhookController.class);

    @Autowired
    private IPaymentService paymentService;

    /**
     * payOS gọi POST tới URL này khi giao dịch hoàn tất.
     * Body chứa data + signature; SDK tự verify bằng checksum key.
     */
    @PostMapping("/payos-hook")
    public ResponseEntity<Map<String, String>> handleWebhook(@RequestBody Map<String, Object> body) {
        try {
            paymentService.handleWebhook(body);
            return ResponseEntity.ok(Map.of("message", "OK"));
        } catch (Exception e) {
            log.error("PayOS webhook error: {}", e.getMessage(), e);
            // Vẫn trả 200 để payOS không retry liên tục khi lỗi logic
            return ResponseEntity.ok(Map.of("message", "Webhook received, processing error logged."));
        }
    }
}
