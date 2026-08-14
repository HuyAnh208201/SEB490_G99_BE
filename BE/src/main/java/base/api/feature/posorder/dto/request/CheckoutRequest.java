package base.api.feature.posorder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Cashier chốt một đơn tại quầy.
 *
 * Cố ý KHÔNG nhận giá, tiền giảm hay tổng tiền từ client — tất cả được tính lại
 * ở server từ bảng products và voucher, nếu không thì ai cũng có thể tự đặt giá.
 */
@Data
public class CheckoutRequest {

    @NotEmpty(message = "Cart is empty.")
    @Valid
    private List<CheckoutLineRequest> lines = new ArrayList<>();

    /** CASH hoặc PAYOS. */
    @NotNull(message = "Payment method is required.")
    @Pattern(regexp = "CASH|PAYOS", message = "Payment method must be CASH or PAYOS.")
    private String paymentMethod;

    /** Tiền khách đưa, chỉ dùng cho CASH để tính tiền thối. */
    @DecimalMin(value = "0", message = "Cash received must be greater than or equal to 0.")
    private BigDecimal cashReceived;

    /** SĐT khách; bỏ trống nghĩa là khách vãng lai không tích điểm. */
    @Size(max = 20, message = "Phone number is too long.")
    private String customerPhone;

    /** Tên khách — chỉ dùng khi SĐT chưa có trong hệ thống, để tạo nhanh. */
    @Size(max = 100, message = "Customer name is too long.")
    private String customerName;

    /** Unused by POS checkout; kept so older clients sending a code are ignored. */
    @Size(max = 64, message = "Discount code is too long.")
    private String voucherCode;

    /** Số điểm khách muốn đổi; server tự chặn trên theo giá trị đơn. */
    @Min(value = 0, message = "Redeemed points must be greater than or equal to 0.")
    private Long pointsToRedeem;
}
