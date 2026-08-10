package base.api.feature.dispatch.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Một yêu cầu nhập hàng đã duyệt, sẵn sàng để gom vào lô vận chuyển (màn Dispatch Planning).
 */
@Getter
@Setter
public class DispatchApprovedRequestResponse {
    private Long id;
    private String requestNumber;
    private Long branchId;
    private String branchName;
    private String area;
    private String route;
    private List<String> categories;
    /** True when any line item belongs to a category flagged short_date. */
    private Boolean hasShortDateCategories;
    private List<String> shortDateCategories;
    private Integer itemCount;
    private LocalDateTime createdAt;
}
