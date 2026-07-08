package base.api.feature.promotion.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class CampaignSummaryResponse {

    private Long id;
    private String name;
    private String type;
    private BigDecimal discountValue;
    private String scope;
    private Integer priority;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private Long createdBy;
    private LocalDateTime createdAt;
    private List<Long> branchIds;
    /** True when this promotion is turned off for the BM's branch (chain-wide opt-out). */
    private Boolean deactivatedForBranch;
}
