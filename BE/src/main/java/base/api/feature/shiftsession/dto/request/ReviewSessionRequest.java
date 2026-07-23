package base.api.feature.shiftsession.dto.request;

import lombok.Getter;
import lombok.Setter;

/**
 * BM gửi lên khi phê duyệt (approve) hoặc từ chối (reject) chênh lệch tiền ca
 * của một shift session. Ghi chú bắt buộc khi reject được kiểm ở tầng service.
 */
@Getter
@Setter
public class ReviewSessionRequest {

    private String note;
}
