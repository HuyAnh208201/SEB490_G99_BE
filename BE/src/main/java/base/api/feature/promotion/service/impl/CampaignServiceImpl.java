package base.api.feature.promotion.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.promotion.dto.request.CreateCampaignRequest;
import base.api.feature.promotion.dto.request.UpdateCampaignRequest;
import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.feature.promotion.mapper.CampaignMapper;
import base.api.feature.promotion.repository.CampaignBranchRepository;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.feature.promotion.service.ICampaignService;
import base.api.shared.entity.CampaignBranchModel;
import base.api.shared.entity.CampaignModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.CampaignScope;
import base.api.shared.enums.CampaignStatus;
import base.api.shared.enums.CampaignType;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CampaignServiceImpl implements ICampaignService {

    private static final Set<String> VALID_PROMOTION_UNITS = Set.of(
            "cai",
            "chai",
            "lon",
            "goi",
            "hop",
            "thung",
            "kg",
            "gram",
            "lit",
            "ml",
            "bao",
            "vi",
            "tui",
            "cuon",
            "cai_doi"
    );

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignBranchRepository campaignBranchRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private ICategoryRepository categoryRepository;

    @Autowired
    private CampaignMapper campaignMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();
        CampaignScope requestedScope = parseScope(request.getScope());

        String normalizedName = normalizeRequiredText(request.getName(), "Promotion name is required.");
        validateDuplicateName(normalizedName, null);
        CampaignType campaignType = parseType(request.getType());

        CampaignModel campaign = new CampaignModel();
        campaign.setName(normalizedName);
        campaign.setType(campaignType);
        campaign.setDiscountValue(validateDiscountValue(request.getDiscountValue()));
        campaign.setConditions(serializeConditions(campaignType, request.getConditions()));
        campaign.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        campaign.setStartAt(request.getStartAt());
        campaign.setEndAt(request.getEndAt());
        campaign.setStatus(CampaignStatus.DEACTIVATED);
        campaign.setCreatedBy(currentUser.getId());

        List<Long> branchIds;
        if (canManageChainPromotions(currentRole)) {
            if (requestedScope != CampaignScope.CHAIN) {
                throw new BadRequestException("Administrator and promotion director can only create chain promotions.");
            }
            campaign.setScope(CampaignScope.CHAIN);
            branchIds = normalizeBranchIds(request.getBranchIds());
            validateBranchesExist(branchIds);
        } else if (currentRole == UserRole.BRANCH_MANAGER) {
            if (requestedScope != CampaignScope.BRANCH) {
                throw new ForbiddenException("Branch manager cannot create chain promotions.");
            }
            Long branchId = resolveCurrentBranchId(currentUser);
            validateBranchesExist(List.of(branchId));
            campaign.setScope(CampaignScope.BRANCH);
            branchIds = List.of(branchId);
        } else {
            throw new ForbiddenException("Access denied.");
        }

        CampaignModel savedCampaign = campaignRepository.save(campaign);
        saveCampaignBranches(savedCampaign.getId(), branchIds);

        return campaignMapper.toResponse(savedCampaign, branchIds);
    }

    @Override
    @Transactional
    public CampaignResponse updateCampaign(Long id, UpdateCampaignRequest request) {
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanModifyCampaign(campaign);

        String normalizedName = normalizeRequiredText(request.getName(), "Promotion name is required.");
        validateDuplicateName(normalizedName, id);
        CampaignType campaignType = parseType(request.getType());

        campaign.setName(normalizedName);
        campaign.setType(campaignType);
        campaign.setDiscountValue(validateDiscountValue(request.getDiscountValue()));
        campaign.setConditions(serializeConditions(campaignType, request.getConditions()));
        campaign.setPriority(request.getPriority());
        campaign.setStartAt(request.getStartAt());
        campaign.setEndAt(request.getEndAt());

        return campaignMapper.toResponse(campaignRepository.save(campaign), getBranchIds(id));
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id) {
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanModifyCampaign(campaign);

        campaignBranchRepository.deleteByCampaignId(id);
        campaignRepository.delete(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse activateCampaign(Long id) {
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanChangeCampaignStatus(campaign);

        if (campaign.getStatus() == CampaignStatus.ACTIVE) {
            throw new BadRequestException("Promotion is already active.");
        }
        if (campaign.getStatus() != CampaignStatus.DRAFT
                && campaign.getStatus() != CampaignStatus.SUSPENDED
                && campaign.getStatus() != CampaignStatus.DEACTIVATED) {
            throw new BadRequestException("Invalid promotion status flow.");
        }

        campaign.setStatus(CampaignStatus.ACTIVE);
        return campaignMapper.toResponse(campaignRepository.save(campaign), getBranchIds(id));
    }

    @Override
    @Transactional
    public CampaignResponse suspendCampaign(Long id) {
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanChangeCampaignStatus(campaign);

        if (campaign.getStatus() == CampaignStatus.DEACTIVATED) {
            throw new BadRequestException("Promotion is already deactivated.");
        }
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new BadRequestException("Only active promotions can be deactivated.");
        }

        campaign.setStatus(CampaignStatus.DEACTIVATED);
        return campaignMapper.toResponse(campaignRepository.save(campaign), getBranchIds(id));
    }

    @Override
    @Transactional
    public CampaignResponse deactivateCampaignForBranch(Long id) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();
        if (currentRole != UserRole.BRANCH_MANAGER) {
            throw new ForbiddenException("Access denied.");
        }

        Long branchId = resolveCurrentBranchId(currentUser);
        CampaignModel campaign = findCampaignOrThrow(id);
        if (campaign.getScope() != CampaignScope.CHAIN) {
            throw new ForbiddenException("Cannot deactivate branch promotions with this action.");
        }

        List<Long> branchIds = getBranchIds(id);
        if (branchIds.isEmpty()) {
            // TODO: Add a branch-level campaign exclusion/status table to support deactivation
            // for entire-chain promotions without changing the global campaign status.
            throw new BadRequestException("Branch-level deactivation for entire-chain promotions is not supported by current schema.");
        }

        CampaignBranchModel branchMapping = campaignBranchRepository.findByCampaignIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ConflictException("Promotion already deactivated for this branch."));

        campaignBranchRepository.delete(branchMapping);
        List<Long> remainingBranchIds = getBranchIds(id);
        return campaignMapper.toResponse(campaign, remainingBranchIds);
    }

    @Override
    @Transactional
    public CampaignResponse activateCampaignForBranch(Long id) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();
        if (currentRole != UserRole.BRANCH_MANAGER) {
            throw new ForbiddenException("Access denied.");
        }

        Long branchId = resolveCurrentBranchId(currentUser);
        CampaignModel campaign = findCampaignOrThrow(id);
        if (campaign.getScope() != CampaignScope.CHAIN) {
            throw new ForbiddenException("Cannot activate branch promotions with this action.");
        }

        List<Long> branchIds = getBranchIds(id);
        if (branchIds.isEmpty()) {
            // TODO: Add a branch-level campaign exclusion/status table to support reactivation
            // for entire-chain promotions without changing the global campaign status.
            throw new BadRequestException("Branch-level activation for entire-chain promotions is not supported by current schema.");
        }

        if (!campaignBranchRepository.existsByCampaignIdAndBranchId(id, branchId)) {
            // TODO: Add a branch-level campaign status table. With only campaign_branches,
            // a missing row means the branch is not currently applied to this campaign.
            throw new ForbiddenException("Promotion is not applied to this branch.");
        }

        return campaignMapper.toResponse(campaign, getBranchIds(id));
    }

    @Override
    public CampaignResponse getCampaign(Long id) {
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanViewCampaign(campaign);
        return campaignMapper.toResponse(campaign, getBranchIds(id));
    }

    @Override
    public List<CampaignSummaryResponse> getAllCampaigns() {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        List<CampaignModel> campaigns;
        if (canManageChainPromotions(currentRole)) {
            campaigns = campaignRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        } else if (currentRole == UserRole.BRANCH_MANAGER) {
            Long branchId = resolveCurrentBranchId(currentUser);
            campaigns = findCampaignsVisibleToBranch(branchId);
        } else {
            throw new ForbiddenException("Access denied.");
        }

        Map<Long, List<Long>> branchIdsByCampaign = loadBranchIdsByCampaign(campaigns);
        return campaigns.stream()
                .map(campaign -> campaignMapper.toSummaryResponse(
                        campaign,
                        branchIdsByCampaign.getOrDefault(campaign.getId(), List.of())))
                .toList();
    }

    private List<CampaignModel> findCampaignsVisibleToBranch(Long branchId) {
        Map<Long, CampaignModel> visibleCampaigns = new LinkedHashMap<>();

        campaignRepository.findByScopeOrderByIdAsc(CampaignScope.CHAIN)
                .forEach(campaign -> visibleCampaigns.put(campaign.getId(), campaign));

        List<Long> campaignIds = campaignBranchRepository.findCampaignIdsByBranchId(branchId);
        if (!campaignIds.isEmpty()) {
            campaignRepository.findByIdIn(campaignIds).forEach(campaign -> visibleCampaigns.put(campaign.getId(), campaign));
        }

        return visibleCampaigns.values().stream()
                .sorted(Comparator.comparing(CampaignModel::getId))
                .toList();
    }

    private void assertCanViewCampaign(CampaignModel campaign) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        if (canManageChainPromotions(currentRole)) {
            return;
        }

        if (currentRole == UserRole.BRANCH_MANAGER) {
            if (campaign.getScope() == CampaignScope.CHAIN) {
                return;
            }
            Long branchId = resolveCurrentBranchId(currentUser);
            if (campaignBranchRepository.existsByCampaignIdAndBranchId(campaign.getId(), branchId)) {
                return;
            }
        }

        throw new ForbiddenException("Access denied.");
    }

    private void assertCanModifyCampaign(CampaignModel campaign) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        if (currentRole == UserRole.ADMIN) {
            return;
        }

        if (!currentUser.getId().equals(campaign.getCreatedBy())) {
            throw new ForbiddenException("Cannot modify promotions created by others.");
        }

        if (currentRole == UserRole.DIRECTOR && campaign.getScope() == CampaignScope.CHAIN) {
            return;
        }

        if (currentRole == UserRole.BRANCH_MANAGER && campaign.getScope() == CampaignScope.BRANCH) {
            Long branchId = resolveCurrentBranchId(currentUser);
            if (campaignBranchRepository.existsByCampaignIdAndBranchId(campaign.getId(), branchId)) {
                return;
            }
        }

        throw new ForbiddenException("Access denied.");
    }

    private void assertCanChangeCampaignStatus(CampaignModel campaign) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        if (canManageChainPromotions(currentRole)) {
            return;
        }

        if (currentRole == UserRole.BRANCH_MANAGER && campaign.getScope() == CampaignScope.BRANCH) {
            if (!currentUser.getId().equals(campaign.getCreatedBy())) {
                throw new ForbiddenException("Cannot modify promotions created by others.");
            }

            Long branchId = resolveCurrentBranchId(currentUser);
            if (campaignBranchRepository.existsByCampaignIdAndBranchId(campaign.getId(), branchId)) {
                return;
            }
        }

        throw new ForbiddenException("Access denied.");
    }

    private boolean canManageChainPromotions(UserRole role) {
        return role == UserRole.ADMIN || role == UserRole.DIRECTOR;
    }

    private Long resolveCurrentBranchId(UserModel currentUser) {
        if (currentUser.getBranchId() == null) {
            throw new BadRequestException("Branch manager is not assigned to a branch.");
        }
        validateBranchesExist(List.of(currentUser.getBranchId()));
        return currentUser.getBranchId();
    }

    private CampaignModel findCampaignOrThrow(Long id) {
        return campaignRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Promotion not found."));
    }

    private void validateDuplicateName(String name, Long currentId) {
        boolean exists = currentId == null
                ? campaignRepository.existsByNameIgnoreCase(name)
                : campaignRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);
        if (exists) {
            throw new ConflictException("Promotion already exists.");
        }
    }

    private BigDecimal validateDiscountValue(BigDecimal discountValue) {
        if (discountValue == null) {
            throw new BadRequestException("Discount value is required.");
        }
        if (discountValue.compareTo(BigDecimal.ZERO) < 0) {
            throw new BadRequestException("Discount value must be greater than or equal to 0.");
        }
        return discountValue;
    }

    private CampaignType parseType(String value) {
        String normalized = normalizeEnumToken(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Promotion type is required.");
        }
        try {
            return CampaignType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid promotion type.");
        }
    }

    private CampaignScope parseScope(String value) {
        String normalized = normalizeEnumToken(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Promotion scope is required.");
        }
        try {
            return CampaignScope.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid promotion scope.");
        }
    }

    private String normalizeEnumToken(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null) {
            return null;
        }
        return normalized.replace(" ", "_").toUpperCase();
    }

    private String normalizeRequiredText(String value, String blankMessage) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(blankMessage);
        }
        if (normalized.length() > 255) {
            throw new BadRequestException("Promotion name must not exceed 255 characters.");
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private List<Long> normalizeBranchIds(Collection<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return List.of();
        }

        Set<Long> normalized = new LinkedHashSet<>();
        for (Long branchId : branchIds) {
            if (branchId == null) {
                throw new BadRequestException("Branch ID is required.");
            }
            normalized.add(branchId);
        }
        return new ArrayList<>(normalized);
    }

    private void validateBranchesExist(List<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return;
        }
        if (branchRepository.findAllById(branchIds).size() != branchIds.size()) {
            throw new NotFoundException("Branch not found.");
        }
    }

    private void saveCampaignBranches(Long campaignId, List<Long> branchIds) {
        if (branchIds == null || branchIds.isEmpty()) {
            return;
        }

        List<CampaignBranchModel> campaignBranches = branchIds.stream()
                .map(branchId -> {
                    CampaignBranchModel model = new CampaignBranchModel();
                    model.setCampaignId(campaignId);
                    model.setBranchId(branchId);
                    return model;
                })
                .toList();
        campaignBranchRepository.saveAll(campaignBranches);
    }

    private List<Long> getBranchIds(Long campaignId) {
        return campaignBranchRepository.findByCampaignId(campaignId).stream()
                .map(CampaignBranchModel::getBranchId)
                .sorted()
                .toList();
    }

    private Map<Long, List<Long>> loadBranchIdsByCampaign(List<CampaignModel> campaigns) {
        if (campaigns.isEmpty()) {
            return Map.of();
        }

        List<Long> campaignIds = campaigns.stream().map(CampaignModel::getId).toList();
        Map<Long, List<Long>> branchIdsByCampaign = new LinkedHashMap<>();
        campaignBranchRepository.findByCampaignIdIn(campaignIds).forEach(campaignBranch ->
                branchIdsByCampaign
                        .computeIfAbsent(campaignBranch.getCampaignId(), key -> new ArrayList<>())
                        .add(campaignBranch.getBranchId()));

        branchIdsByCampaign.values().forEach(branchIds -> branchIds.sort(Long::compareTo));
        return branchIdsByCampaign;
    }

    private String serializeConditions(CampaignType type, JsonNode conditions) {
        JsonNode normalizedConditions = normalizeJsonStrings(conditions);
        validateConditions(type, normalizedConditions);
        if (normalizedConditions == null || normalizedConditions.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizedConditions);
        } catch (Exception ex) {
            throw new BadRequestException("Invalid promotion conditions.");
        }
    }

    private void validateConditions(CampaignType type, JsonNode conditions) {
        if (type != CampaignType.BUY_X_GET_Y) {
            return;
        }

        if (conditions == null || conditions.isNull() || !conditions.isObject()) {
            throw new BadRequestException("Buy X get Y conditions are required.");
        }

        requirePositiveInteger(conditions, "buyQuantity", "Buy quantity must be greater than 0.");
        requirePositiveInteger(conditions, "getQuantity", "Get quantity must be greater than 0.");
        int categoryId = requirePositiveInteger(conditions, "categoryId", "Product category is required.");
        if (!categoryRepository.existsById(categoryId)) {
            throw new NotFoundException("Category not found.");
        }

        String unit = normalizePromotionUnit(readRequiredText(conditions, "unit", "Unit is required."));
        if (conditions instanceof ObjectNode objectNode) {
            objectNode.set("unit", TextNode.valueOf(unit));
        }
    }

    private int requirePositiveInteger(JsonNode node, String field, String message) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() <= 0) {
            throw new BadRequestException(message);
        }
        return value.asInt();
    }

    private String readRequiredText(JsonNode node, String field, String message) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw new BadRequestException(message);
        }

        String normalized = normalizeWhitespace(value.asText());
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException(message);
        }
        return normalized;
    }

    private String normalizePromotionUnit(String unit) {
        String normalized = unit.toLowerCase(Locale.ROOT);
        if (!VALID_PROMOTION_UNITS.contains(normalized)) {
            throw new BadRequestException("Invalid promotion unit.");
        }
        return normalized;
    }

    private JsonNode normalizeJsonStrings(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return TextNode.valueOf(normalizeWhitespace(node.asText()));
        }

        if (node.isArray()) {
            ArrayNode arrayNode = objectMapper.createArrayNode();
            node.forEach(child -> arrayNode.add(normalizeJsonStrings(child)));
            return arrayNode;
        }

        if (node.isObject()) {
            ObjectNode objectNode = objectMapper.createObjectNode();
            for (Map.Entry<String, JsonNode> field : node.properties()) {
                objectNode.set(field.getKey(), normalizeJsonStrings(field.getValue()));
            }
            return objectNode;
        }

        return node.deepCopy();
    }
}
