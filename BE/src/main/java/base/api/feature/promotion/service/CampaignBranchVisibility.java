package base.api.feature.promotion.service;

import base.api.feature.promotion.repository.CampaignBranchExclusionRepository;
import base.api.feature.promotion.repository.CampaignBranchRepository;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.shared.entity.CampaignBranchExclusionModel;
import base.api.shared.entity.CampaignBranchModel;
import base.api.shared.entity.CampaignModel;
import base.api.shared.enums.CampaignScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Shared campaign-to-branch visibility:
 * <pre>
 * visible(branchId) =
 *   not excluded(branchId)
 *   AND (
 *     (scope == CHAIN && (no campaign_branches links OR links.contains(branchId)))
 *     OR (scope == BRANCH && links.contains(branchId))
 *   )
 * </pre>
 */
@Service
public class CampaignBranchVisibility {

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignBranchRepository campaignBranchRepository;

    @Autowired
    private CampaignBranchExclusionRepository campaignBranchExclusionRepository;

    public boolean isVisibleToBranch(CampaignModel campaign, Long branchId) {
        if (campaign == null || branchId == null) {
            return false;
        }
        if (campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(campaign.getId(), branchId)) {
            return false;
        }
        List<Long> linkedBranchIds = campaignBranchRepository.findByCampaignId(campaign.getId()).stream()
                .map(CampaignBranchModel::getBranchId)
                .toList();
        return matchesVisibilityRule(campaign, branchId, linkedBranchIds);
    }

    public List<CampaignModel> findCampaignsVisibleToBranch(Long branchId) {
        if (branchId == null) {
            return List.of();
        }

        Map<Long, CampaignModel> candidates = new LinkedHashMap<>();
        campaignRepository.findByScopeOrderByIdAsc(CampaignScope.CHAIN)
                .forEach(campaign -> candidates.put(campaign.getId(), campaign));

        List<Long> linkedCampaignIds = campaignBranchRepository.findCampaignIdsByBranchId(branchId);
        if (!linkedCampaignIds.isEmpty()) {
            campaignRepository.findByIdIn(linkedCampaignIds)
                    .forEach(campaign -> candidates.put(campaign.getId(), campaign));
        }

        if (candidates.isEmpty()) {
            return List.of();
        }

        Set<Long> excludedCampaignIds = campaignBranchExclusionRepository.findByBranchId(branchId).stream()
                .map(CampaignBranchExclusionModel::getCampaignId)
                .collect(Collectors.toSet());

        Map<Long, List<Long>> linkedBranchIdsByCampaign = loadLinkedBranchIdsByCampaign(candidates.keySet());

        return candidates.values().stream()
                .filter(campaign -> isVisibleToBranch(
                        campaign,
                        branchId,
                        linkedBranchIdsByCampaign.getOrDefault(campaign.getId(), List.of()),
                        excludedCampaignIds))
                .sorted(Comparator.comparing(CampaignModel::getId))
                .toList();
    }

    public Set<Long> findVisibleCampaignIdsForBranch(Long branchId) {
        return findCampaignsVisibleToBranch(branchId).stream()
                .map(CampaignModel::getId)
                .collect(Collectors.toSet());
    }

    boolean isVisibleToBranch(
            CampaignModel campaign,
            Long branchId,
            List<Long> linkedBranchIds,
            Set<Long> excludedCampaignIds) {
        if (campaign == null || branchId == null) {
            return false;
        }
        if (excludedCampaignIds.contains(campaign.getId())) {
            return false;
        }
        return matchesVisibilityRule(campaign, branchId, linkedBranchIds);
    }

    private static boolean matchesVisibilityRule(
            CampaignModel campaign,
            Long branchId,
            List<Long> linkedBranchIds) {
        if (campaign.getScope() == CampaignScope.CHAIN) {
            return linkedBranchIds.isEmpty() || linkedBranchIds.contains(branchId);
        }
        if (campaign.getScope() == CampaignScope.BRANCH) {
            return linkedBranchIds.contains(branchId);
        }
        return false;
    }

    private Map<Long, List<Long>> loadLinkedBranchIdsByCampaign(Collection<Long> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, List<Long>> linkedBranchIdsByCampaign = new LinkedHashMap<>();
        campaignBranchRepository.findByCampaignIdIn(campaignIds).forEach(campaignBranch ->
                linkedBranchIdsByCampaign
                        .computeIfAbsent(campaignBranch.getCampaignId(), key -> new ArrayList<>())
                        .add(campaignBranch.getBranchId()));
        linkedBranchIdsByCampaign.values().forEach(branchIds -> branchIds.sort(Long::compareTo));
        return linkedBranchIdsByCampaign;
    }
}
