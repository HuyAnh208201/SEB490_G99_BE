package base.api.feature.cashier.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Trả về sau khi chốt điểm cho hoá đơn.
 */
@Data
@AllArgsConstructor
public class AddPointsResponse {

    /** Tên đầy đủ của khách hàng. */
    private String customerName;

    /** Email của khách hàng. */
    private String customerEmail;

    /** Số điểm vừa bị trừ do khách đổi lấy giảm giá. */
    private long pointsRedeemed;

    /** Số điểm vừa được cộng thêm từ hóa đơn này. */
    private long pointsEarned;

    /** Tổng điểm hiện tại sau khi cộng. */
    private long totalPoints;

    /** Số tiền hóa đơn để xác nhận lại với cashier. */
    private BigDecimal invoiceAmount;
}
