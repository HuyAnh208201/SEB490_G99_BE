package base.api.feature.posorder.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * BM gửi ghi chú khi duyệt (approve) hoặc từ chối (reject) một yêu cầu hoàn/trả.
 * Ghi chú bắt buộc khi reject được kiểm ở tầng service.
 */
@Getter
@Setter
public class RefundReviewBody {

    @Size(max = 500, message = "Note is too long.")
    private String note;
}
