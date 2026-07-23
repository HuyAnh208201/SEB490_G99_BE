package base.api.feature.cashier.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Cashier chốt điểm cho một hoá đơn: trừ điểm khách đổi lấy giảm giá và cộng
 * điểm kiếm được từ số tiền thực trả. Cả hai chạy trong cùng một transaction.
 */
@Data
public class AddPointsRequest {

    /**
     * SĐT hoặc email dùng để tra cứu khách hàng.
     */
    @NotBlank(message = "Customer phone or email is required.")
    private String phoneOrEmail;

    /**
     * Tổng tiền khách thực trả (VNĐ), tức là đã trừ giảm giá và điểm đổi.
     * Hệ thống tự tính điểm cộng từ số này.
     */
    @NotNull(message = "Invoice total is required.")
    @DecimalMin(value = "0", message = "Invoice total must be greater than or equal to 0.")
    private BigDecimal invoiceAmount;

    /**
     * Số điểm khách dùng để giảm giá cho hoá đơn này. Bỏ trống hoặc 0 nghĩa là
     * không đổi điểm. Không đủ điểm thì cả request bị từ chối.
     */
    @Min(value = 0, message = "Redeemed points must be greater than or equal to 0.")
    private Long pointsToRedeem;
}
