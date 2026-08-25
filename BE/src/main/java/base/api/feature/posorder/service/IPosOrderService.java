package base.api.feature.posorder.service;

import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.ApplicablePromotionResponse;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface IPosOrderService {

    /**
     * Chốt một đơn tại quầy: ghi hoá đơn, trừ tồn kho và chốt điểm
     * — tất cả trong một transaction. Hỏng bất kỳ bước nào thì
     * không có gì được ghi.
     */
    OrderResponse checkout(CheckoutRequest request);

    /**
     * ACTIVE campaigns visible to the cashier's branch, each with eligibility
     * against {@code subtotal} (min order + supported types).
     */
    List<ApplicablePromotionResponse> listApplicablePromotions(BigDecimal subtotal);

    /** Lịch sử đơn của chi nhánh đang đăng nhập. */
    List<OrderResponse> getOrders(LocalDate from, LocalDate to);

    Page<OrderResponse> getOrderPage(
            PageRequestDTO pageRequest,
            LocalDate from,
            LocalDate to,
            String paymentMethod);

    OrderResponse getOrderById(Long id);
}
