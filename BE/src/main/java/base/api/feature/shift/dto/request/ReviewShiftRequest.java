package base.api.feature.shift.dto.request;

import lombok.Data;

/**
 * BM gửi lên khi phê duyệt hoặc từ chối chênh lệch tiền ca.
 */
@Data
public class ReviewShiftRequest {

    /**
     * Ghi chú của BM khi duyệt hoặc từ chối.
     * VD khi reject: "Chênh lệch quá lớn, yêu cầu đếm lại."
     * VD khi approve: "Đã xác nhận, chênh lệch nằm trong giới hạn cho phép."
     */
    private String note;
}
