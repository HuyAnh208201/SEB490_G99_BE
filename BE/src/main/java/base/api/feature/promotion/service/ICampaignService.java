package base.api.feature.promotion.service;

import base.api.feature.promotion.dto.request.ActivateCampaignRequest;
import base.api.feature.promotion.dto.request.CreateCampaignRequest;
import base.api.feature.promotion.dto.request.UpdateCampaignRequest;
import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.enums.CampaignStatus;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ICampaignService {

    CampaignResponse createCampaign(CreateCampaignRequest request);

    CampaignResponse updateCampaign(Long id, UpdateCampaignRequest request);

    void deleteCampaign(Long id);

    CampaignResponse activateCampaign(Long id);

    CampaignResponse activateCampaign(Long id, ActivateCampaignRequest request);

    CampaignResponse suspendCampaign(Long id);

    CampaignResponse deactivateCampaignForBranch(Long id);

    CampaignResponse activateCampaignForBranch(Long id);

    CampaignResponse getCampaign(Long id);

    List<CampaignSummaryResponse> getAllCampaigns();

    /**
     * Khuyến mãi đang áp được cho một chi nhánh tại thời điểm gọi, đã sắp theo đúng
     * thứ tự áp lên đơn. Quầy bán hàng gọi hàm này thay vì tự lọc — luật "campaign nào
     * áp cho chi nhánh nào" chỉ được sống một chỗ.
     */
    List<CampaignSummaryResponse> getApplicableForBranch(Long branchId);

    Page<CampaignSummaryResponse> getCampaignPage(
            PageRequestDTO pageRequest,
            CampaignStatus status,
            Long branchId,
            String creatorTier);
}
