package base.api.feature.promotion.service;

import base.api.feature.promotion.dto.request.CreateCampaignRequest;
import base.api.feature.promotion.dto.request.UpdateCampaignRequest;
import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;

import java.util.List;

public interface ICampaignService {

    CampaignResponse createCampaign(CreateCampaignRequest request);

    CampaignResponse updateCampaign(Long id, UpdateCampaignRequest request);

    void deleteCampaign(Long id);

    CampaignResponse activateCampaign(Long id);

    CampaignResponse suspendCampaign(Long id);

    CampaignResponse deactivateCampaignForBranch(Long id);

    CampaignResponse getCampaign(Long id);

    List<CampaignSummaryResponse> getAllCampaigns();
}
