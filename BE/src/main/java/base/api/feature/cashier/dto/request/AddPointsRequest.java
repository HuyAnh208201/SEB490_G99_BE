package base.api.feature.cashier.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Request Cashier gửi lên để tích điểm cho khách hàng.
 * Cashier nhập SĐT hoặc email của khách và số tiền hóa đơn.
 */
@Data
public class AddPointsRequest {

    /**
     * SĐT hoặc email dùng để tra cứu khách hàng.
     */
    @NotBlank(message = "Vui lòng nhập SĐT hoặc email của khách hàng.")
    private String phoneOrEmail;

    /**
     * Tổng tiền hóa đơn (VNĐ). Hệ thống tự tính điểm từ số này.
     */
    @NotNull(message = "Vui lòng nhập số tiền hóa đơn.")
    @DecimalMin(value = "1000", message = "Số tiền hóa đơn phải ít nhất 1.000 VNĐ.")
    private BigDecimal invoiceAmount;
}
