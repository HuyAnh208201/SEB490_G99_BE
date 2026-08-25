package base.api.feature.promotion.service;

import base.api.feature.promotion.repository.CampaignBranchExclusionRepository;
import base.api.feature.promotion.repository.CampaignBranchRepository;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignBranchExclusionModel;
import base.api.shared.entity.CampaignBranchModel;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignBranchVisibilityTest {

    private static final Long BRANCH_A = 10L;
    private static final Long BRANCH_B = 20L;

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignBranchRepository campaignBranchRepository;
    @Mock private CampaignBranchExclusionRepository campaignBranchExclusionRepository;

    @InjectMocks
    private CampaignBranchVisibility visibility;

    @Test
    void chainWithNoLinksVisibleToAllBranches() {
        CampaignModel campaign = campaign(1L, CampaignScope.CHAIN);
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, BRANCH_A)).thenReturn(false);
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());

        assertTrue(visibility.isVisibleToBranch(campaign, BRANCH_A));
    }

    @Test
    void chainSubsetVisibleOnlyToLinkedBranch() {
        CampaignModel campaign = campaign(2L, CampaignScope.CHAIN);
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(2L, BRANCH_A)).thenReturn(false);
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(2L, BRANCH_B)).thenReturn(false);
        when(campaignBranchRepository.findByCampaignId(2L)).thenReturn(List.of(link(2L, BRANCH_A)));

        assertTrue(visibility.isVisibleToBranch(campaign, BRANCH_A));
        assertFalse(visibility.isVisibleToBranch(campaign, BRANCH_B));
    }

    @Test
    void branchScopeVisibleOnlyWhenLinked() {
        CampaignModel campaign = campaign(3L, CampaignScope.BRANCH);
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(3L, BRANCH_A)).thenReturn(false);
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(3L, BRANCH_B)).thenReturn(false);
        when(campaignBranchRepository.findByCampaignId(3L)).thenReturn(List.of(link(3L, BRANCH_A)));

        assertTrue(visibility.isVisibleToBranch(campaign, BRANCH_A));
        assertFalse(visibility.isVisibleToBranch(campaign, BRANCH_B));
    }

    @Test
    void exclusionHidesCampaignFromBranch() {
        CampaignModel campaign = campaign(4L, CampaignScope.CHAIN);
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(4L, BRANCH_A)).thenReturn(true);

        assertFalse(visibility.isVisibleToBranch(campaign, BRANCH_A));
    }

    @Test
    void findCampaignsVisibleToBranchFiltersSubsetAndExclusions() {
        CampaignModel chainAll = campaign(1L, CampaignScope.CHAIN);
        CampaignModel chainSubset = campaign(2L, CampaignScope.CHAIN);
        CampaignModel branchPromo = campaign(3L, CampaignScope.BRANCH);
        CampaignModel excludedChain = campaign(4L, CampaignScope.CHAIN);

        when(campaignRepository.findByScopeOrderByIdAsc(CampaignScope.CHAIN))
                .thenReturn(List.of(chainAll, chainSubset, excludedChain));
        when(campaignBranchRepository.findCampaignIdsByBranchId(BRANCH_A))
                .thenReturn(List.of(2L, 3L));
        when(campaignRepository.findByIdIn(List.of(2L, 3L)))
                .thenReturn(List.of(chainSubset, branchPromo));
        when(campaignBranchExclusionRepository.findByBranchId(BRANCH_A)).thenReturn(List.of(
                exclusion(4L, BRANCH_A)));
        when(campaignBranchRepository.findByCampaignIdIn(Set.of(1L, 2L, 3L, 4L))).thenReturn(List.of(
                link(2L, BRANCH_B),
                link(3L, BRANCH_A)));

        List<CampaignModel> visible = visibility.findCampaignsVisibleToBranch(BRANCH_A);

        assertTrue(visible.stream().anyMatch(c -> c.getId().equals(1L)));
        assertFalse(visible.stream().anyMatch(c -> c.getId().equals(2L)));
        assertTrue(visible.stream().anyMatch(c -> c.getId().equals(3L)));
        assertFalse(visible.stream().anyMatch(c -> c.getId().equals(4L)));
    }

    private static CampaignModel campaign(Long id, CampaignScope scope) {
        CampaignModel campaign = new CampaignModel();
        campaign.setId(id);
        campaign.setScope(scope);
        return campaign;
    }

    private static CampaignBranchModel link(Long campaignId, Long branchId) {
        CampaignBranchModel link = new CampaignBranchModel();
        link.setCampaignId(campaignId);
        link.setBranchId(branchId);
        return link;
    }

    private static CampaignBranchExclusionModel exclusion(Long campaignId, Long branchId) {
        CampaignBranchExclusionModel exclusion = new CampaignBranchExclusionModel();
        exclusion.setCampaignId(campaignId);
        exclusion.setBranchId(branchId);
        return exclusion;
    }
}
