package base.api.feature.promotion.repository;

import base.api.shared.entity.CampaignBranchModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CampaignBranchRepository extends JpaRepository<CampaignBranchModel, Long> {

    List<CampaignBranchModel> findByCampaignId(Long campaignId);

    List<CampaignBranchModel> findByCampaignIdIn(Collection<Long> campaignIds);

    void deleteByCampaignId(Long campaignId);

    boolean existsByCampaignIdAndBranchId(Long campaignId, Long branchId);

    Optional<CampaignBranchModel> findByCampaignIdAndBranchId(Long campaignId, Long branchId);

    @Query("SELECT DISTINCT cb.campaignId FROM CampaignBranchModel cb WHERE cb.branchId = :branchId")
    List<Long> findCampaignIdsByBranchId(@Param("branchId") Long branchId);
}
