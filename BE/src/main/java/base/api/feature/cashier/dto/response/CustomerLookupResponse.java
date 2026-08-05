package base.api.feature.cashier.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Thông tin khách hàng trả về khi cashier tra cứu bằng SĐT hoặc email.
 */
@Data
@AllArgsConstructor
public class CustomerLookupResponse {

    /** ID của khách hàng. */
    private Long customerId;

    /** Tên đầy đủ. */
    private String fullName;

    /** Email. */
    private String email;

    /** Số điện thoại. */
    private String phone;

    /** Tổng điểm tích lũy hiện tại. */
    private long totalPoints;

    /** Membership tier code (e.g. SILVER), null if unset. */
    private String tierCode;

    /** Membership tier display name, null if unset. */
    private String tierName;
}
