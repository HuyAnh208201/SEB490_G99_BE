package base.api.feature.shift.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Staff (Cashier/Inventory) gửi lên khi đóng ca cuối ngày.
 * Staff đếm tiền thực tế và nhập vào đây.
 */
@Data
public class CloseShiftRequest {

    /**
     * Số tiền thực tế đếm được khi đóng ca.
     * Hệ thống sẽ tự tính chênh lệch = actual - expected.
     */
    @NotNull(message = "Vui lòng nhập số tiền thực tế đếm được.")
    @DecimalMin(value = "0", message = "Số tiền không được âm.")
    private BigDecimal actualCash;

    /**
     * Ghi chú của staff khi đóng ca (tuỳ chọn).
     * VD: "Đã đếm lại 2 lần, đúng số tiền trên."
     */
    private String note;
}
