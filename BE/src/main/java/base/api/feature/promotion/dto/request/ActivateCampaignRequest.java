package base.api.feature.promotion.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ActivateCampaignRequest {
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
