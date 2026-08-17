package base.api.feature.branchreceiving.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SupplementalRequestResponse {
    private Long requestId;
    private String requestNumber;
    private Integer productCount;
    private boolean existing;
}
