package base.api.feature.promotion.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampaignResponse extends CampaignSummaryResponse {

    private JsonNode conditions;
}
