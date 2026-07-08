package base.api.feature.promotion.mapper;

import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.shared.entity.CampaignModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CampaignMapper {

    @Autowired
    private ObjectMapper objectMapper;

    public CampaignSummaryResponse toSummaryResponse(CampaignModel campaign, List<Long> branchIds) {
        return toSummaryResponse(campaign, branchIds, null);
    }

    public CampaignSummaryResponse toSummaryResponse(
            CampaignModel campaign,
            List<Long> branchIds,
            Boolean deactivatedForBranch) {
        CampaignSummaryResponse response = new CampaignSummaryResponse();
        mapBaseFields(response, campaign, branchIds);
        response.setDeactivatedForBranch(deactivatedForBranch);
        return response;
    }

    public CampaignResponse toResponse(CampaignModel campaign, List<Long> branchIds) {
        CampaignResponse response = new CampaignResponse();
        mapBaseFields(response, campaign, branchIds);
        response.setConditions(parseConditions(campaign.getConditions()));
        return response;
    }

    private void mapBaseFields(CampaignSummaryResponse response, CampaignModel campaign, List<Long> branchIds) {
        response.setId(campaign.getId());
        response.setName(campaign.getName());
        response.setType(campaign.getType() != null ? campaign.getType().name() : null);
        response.setDiscountValue(campaign.getDiscountValue());
        response.setScope(campaign.getScope() != null ? campaign.getScope().name() : null);
        response.setPriority(campaign.getPriority());
        response.setStatus(campaign.getStatus() != null ? campaign.getStatus().name() : null);
        response.setStartAt(campaign.getStartAt());
        response.setEndAt(campaign.getEndAt());
        response.setCreatedBy(campaign.getCreatedBy());
        response.setCreatedAt(campaign.getCreatedAt());
        response.setBranchIds(branchIds);
    }

    private JsonNode parseConditions(String conditions) {
        if (conditions == null || conditions.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(conditions);
        } catch (Exception ex) {
            return null;
        }
    }
}
