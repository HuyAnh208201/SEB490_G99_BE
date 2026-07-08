package base.api.feature.promotion.repository;

import base.api.shared.entity.CampaignBranchExclusionModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CampaignBranchExclusionRepository extends JpaRepository<CampaignBranchExclusionModel, Long> {

    boolean existsByCampaignIdAndBranchId(Long campaignId, Long branchId);

    void deleteByCampaignIdAndBranchId(Long campaignId, Long branchId);

    void deleteByCampaignId(Long campaignId);

    List<CampaignBranchExclusionModel> findByCampaignIdIn(Collection<Long> campaignIds);

    List<CampaignBranchExclusionModel> findByBranchId(Long branchId);
}
