package base.api.feature.cashier.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Tỉ lệ điểm do server quyết định, FE chỉ hiển thị chứ không tự đặt.
 */
@Data
@AllArgsConstructor
public class LoyaltyConfigResponse {

    /** Tiêu bao nhiêu VNĐ thì được 1 điểm. */
    private long vndPerPoint;

    /** 1 điểm đổi được bao nhiêu VNĐ giảm giá. */
    private long pointValueVnd;
}
