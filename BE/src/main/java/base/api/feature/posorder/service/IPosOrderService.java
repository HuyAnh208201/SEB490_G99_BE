package base.api.feature.posorder.service;

import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.dto.response.VoucherResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.time.LocalDate;
import java.util.List;

public interface IPosOrderService {

    /**
     * Chốt một đơn tại quầy: ghi hoá đơn, trừ tồn kho, chốt điểm và đánh dấu
     * voucher đã dùng — tất cả trong một transaction. Hỏng bất kỳ bước nào thì
     * không có gì được ghi.
     */
    OrderResponse checkout(CheckoutRequest request);

    /** Lịch sử đơn của chi nhánh đang đăng nhập. */
    List<OrderResponse> getOrders(LocalDate from, LocalDate to);

    Page<OrderResponse> getOrderPage(
            PageRequestDTO pageRequest,
            LocalDate from,
            LocalDate to,
            String paymentMethod);

    OrderResponse getOrderById(Long id);

    /**
     * Campaigns that apply to the order being rung up, taken from the cashier's own
     * branch. {@link #checkout} resolves the same set again and applies all of it; this
     * call exists so the screen can show the customer what is being taken off.
     */
    List<CampaignSummaryResponse> getApplicablePromotions();

    /**
     * Looks a voucher up before checkout so the cashier sees the discount immediately.
     * Pass the customer phone when known so a code issued to somebody else is refused
     * at lookup instead of failing at checkout.
     */
    VoucherResponse lookupVoucher(String code, String customerPhone);

    default VoucherResponse lookupVoucher(String code) {
        return lookupVoucher(code, null);
    }
}
