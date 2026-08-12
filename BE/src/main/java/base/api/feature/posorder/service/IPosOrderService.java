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
     * Khuyến mãi cashier chọn được cho đơn đang lập, lấy theo chi nhánh của chính
     * cashier đó. Đây cũng là tập hợp mà {@link #checkout} kiểm lại — chọn thứ không
     * nằm trong danh sách này thì chốt đơn bị từ chối.
     */
    List<CampaignSummaryResponse> getApplicablePromotions();

    /**
     * Tra mã giảm giá trước khi chốt đơn để cashier thấy ngay số tiền giảm.
     * Truyền SĐT khách nếu đã có, để mã phát riêng cho khách khác bị chặn ngay
     * tại bước tra thay vì chờ đến lúc chốt đơn mới báo lỗi.
     */
    VoucherResponse lookupVoucher(String code, String customerPhone);

    default VoucherResponse lookupVoucher(String code) {
        return lookupVoucher(code, null);
    }
}
