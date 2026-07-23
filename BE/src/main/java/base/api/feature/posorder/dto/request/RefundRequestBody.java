package base.api.feature.posorder.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Cashier gửi lý do khi yêu cầu hoàn/trả một đơn. Lý do bắt buộc (không blank)
 * được kiểm ở tầng service để trả về thông điệp nghiệp vụ thống nhất.
 */
@Getter
@Setter
public class RefundRequestBody {

    @Size(max = 500, message = "Reason is too long.")
    private String reason;
}
