package base.api.feature.promotion.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.promotion.dto.request.ActivateCampaignRequest;
import base.api.feature.promotion.dto.request.CreateCampaignRequest;
import base.api.feature.promotion.dto.request.UpdateCampaignRequest;
import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.feature.promotion.mapper.CampaignMapper;
import base.api.feature.promotion.repository.CampaignBranchRepository;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.feature.promotion.service.CampaignBranchVisibility;
import base.api.feature.promotion.service.CampaignExpiryService;
import base.api.feature.promotion.service.ICampaignService;
import base.api.feature.promotion.repository.CampaignBranchExclusionRepository;
import base.api.shared.entity.CampaignBranchExclusionModel;
import base.api.shared.entity.CampaignBranchModel;
import base.api.shared.entity.CampaignModel;
import base.api.shared.entity.UserModel;
import base.api.shared.dto.PageRequestDTO;
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
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class CampaignServiceImpl implements ICampaignService {

    @Autowired
    private CampaignRepository campaignRepository;

    @Autowired
    private CampaignBranchRepository campaignBranchRepository;

    @Autowired
    private CampaignBranchExclusionRepository campaignBranchExclusionRepository;

    @Autowired
    private IBranchRepository branchRepository;

    @Autowired
    private IUserRepository userRepository;

    @Autowired
    private CampaignMapper campaignMapper;

    @Autowired
    private CurrentUserProvider currentUserProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CampaignExpiryService campaignExpiryService;

    @Autowired
    private CampaignBranchVisibility campaignBranchVisibility;

    @Override
    @Transactional
    public CampaignResponse createCampaign(CreateCampaignRequest request) {
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();
        CampaignScope requestedScope = parseScope(request.getScope());

        String normalizedName = normalizeRequiredText(request.getName(), "Promotion name is required.");
        validateDuplicateName(normalizedName, null);

        CampaignModel campaign = new CampaignModel();
        campaign.setName(normalizedName);
        campaign.setType(parseType(request.getType()));
        campaign.setDiscountValue(validateDiscountValue(request.getDiscountValue()));
        campaign.setConditions(serializeConditions(request.getConditions()));
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
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        String normalizedName = normalizeRequiredText(request.getName(), "Promotion name is required.");
        validateDuplicateName(normalizedName, id);

        campaign.setName(normalizedName);
        campaign.setType(parseType(request.getType()));
        campaign.setDiscountValue(validateDiscountValue(request.getDiscountValue()));
        campaign.setConditions(serializeConditions(request.getConditions()));
        campaign.setPriority(request.getPriority());
        campaign.setStartAt(request.getStartAt());
        campaign.setEndAt(request.getEndAt());

        List<Long> branchIds;
        if (canManageChainPromotions(currentRole)) {
            if (request.getScope() != null && !request.getScope().isBlank()) {
                CampaignScope requestedScope = parseScope(request.getScope());
                if (requestedScope != CampaignScope.CHAIN) {
                    throw new BadRequestException(
                            "Administrator and promotion director can only manage chain promotions.");
                }
                campaign.setScope(CampaignScope.CHAIN);
            } else if (campaign.getScope() != CampaignScope.CHAIN) {
                campaign.setScope(CampaignScope.CHAIN);
            }
            branchIds = normalizeBranchIds(request.getBranchIds());
            validateBranchesExist(branchIds);
            campaignRepository.save(campaign);
            replaceCampaignBranches(id, branchIds);
        } else if (currentRole == UserRole.BRANCH_MANAGER) {
            // Branch managers cannot retarget other stores — keep existing mapping.
            campaignRepository.save(campaign);
            branchIds = getBranchIds(id);
        } else {
            throw new ForbiddenException("Access denied.");
        }

        return campaignMapper.toResponse(campaign, branchIds);
    }

    @Override
    @Transactional
    public void deleteCampaign(Long id) {
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanModifyCampaign(campaign);
        if (campaign.getStatus() == CampaignStatus.ACTIVE) {
            throw new BadRequestException(
                    "Active promotions cannot be deleted. Deactivate the promotion first.");
        }

        campaignBranchRepository.deleteByCampaignId(id);
        campaignBranchExclusionRepository.deleteByCampaignId(id);
        campaignRepository.delete(campaign);
    }

    @Override
    @Transactional
    public CampaignResponse activateCampaign(Long id) {
        return activateCampaign(id, null);
    }

    @Override
    @Transactional
    public CampaignResponse activateCampaign(Long id, ActivateCampaignRequest request) {
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

        LocalDateTime now = LocalDateTime.now();
        boolean requestProvidesDates = request != null
                && request.getStartAt() != null
                && request.getEndAt() != null;

        LocalDateTime startAt = requestProvidesDates ? request.getStartAt() : campaign.getStartAt();
        LocalDateTime endAt = requestProvidesDates ? request.getEndAt() : campaign.getEndAt();

        boolean needsNewDates = startAt == null
                || startAt.toLocalDate().isBefore(LocalDate.now())
                || (endAt != null && !endAt.isAfter(now));

        if (needsNewDates && !requestProvidesDates) {
            throw new BadRequestException(
                    "Promotion dates are in the past. Provide a new startAt and endAt to activate.");
        }

        if (requestProvidesDates) {
            if (startAt.toLocalDate().isBefore(LocalDate.now())) {
                throw new BadRequestException("New start date must be today or later.");
            }
            if (!endAt.isAfter(startAt)) {
                throw new BadRequestException("End date must be after start date.");
            }
            if (!endAt.isAfter(now)) {
                throw new BadRequestException("New end date must be in the future.");
            }
            campaign.setStartAt(startAt);
            campaign.setEndAt(endAt);
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
            if (campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(id, branchId)) {
                throw new ConflictException("Promotion already deactivated for this branch.");
            }
            CampaignBranchExclusionModel exclusion = new CampaignBranchExclusionModel();
            exclusion.setCampaignId(id);
            exclusion.setBranchId(branchId);
            campaignBranchExclusionRepository.save(exclusion);
            return campaignMapper.toResponse(campaign, branchIds);
        }

        CampaignBranchModel branchMapping = campaignBranchRepository.findByCampaignIdAndBranchId(id, branchId)
                .orElse(null);
        if (branchMapping != null) {
            campaignBranchRepository.delete(branchMapping);
        } else {
            if (campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(id, branchId)) {
                throw new ConflictException("Promotion already deactivated for this branch.");
            }
            CampaignBranchExclusionModel exclusion = new CampaignBranchExclusionModel();
            exclusion.setCampaignId(id);
            exclusion.setBranchId(branchId);
            campaignBranchExclusionRepository.save(exclusion);
        }
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
            if (!campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(id, branchId)) {
                throw new BadRequestException("Promotion is not deactivated for this branch.");
            }
            campaignBranchExclusionRepository.deleteByCampaignIdAndBranchId(id, branchId);
            return campaignMapper.toResponse(campaign, branchIds);
        }

        if (!campaignBranchRepository.existsByCampaignIdAndBranchId(id, branchId)) {
            throw new ForbiddenException("Promotion is not applied to this branch.");
        }

        return campaignMapper.toResponse(campaign, getBranchIds(id));
    }

    @Override
    public CampaignResponse getCampaign(Long id) {
        campaignExpiryService.deactivateExpiredCampaigns();
        CampaignModel campaign = findCampaignOrThrow(id);
        assertCanViewCampaign(campaign);
        return campaignMapper.toResponse(campaign, getBranchIds(id));
    }

    @Override
    public List<CampaignSummaryResponse> getAllCampaigns() {
        campaignExpiryService.deactivateExpiredCampaigns();
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();

        List<CampaignModel> campaigns;
        if (canManageChainPromotions(currentRole)) {
            campaigns = campaignRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        } else if (currentRole == UserRole.BRANCH_MANAGER) {
            Long branchId = resolveCurrentBranchId(currentUser);
            campaigns = findCampaignsVisibleToBranch(branchId);
            Set<Long> excludedCampaignIds = campaignBranchExclusionRepository.findByBranchId(branchId).stream()
                    .map(CampaignBranchExclusionModel::getCampaignId)
                    .collect(java.util.stream.Collectors.toSet());
            Map<Long, List<Long>> branchIdsByCampaign = loadBranchIdsByCampaign(campaigns);
            return campaigns.stream()
                    .map(campaign -> {
                        List<Long> branchIds = branchIdsByCampaign.getOrDefault(campaign.getId(), List.of());
                        boolean deactivatedForBranch = isDeactivatedForBranch(
                                campaign, branchId, branchIds, excludedCampaignIds);
                        return campaignMapper.toSummaryResponse(campaign, branchIds, deactivatedForBranch);
                    })
                    .toList();
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

    @Override
    public Page<CampaignSummaryResponse> getCampaignPage(
            PageRequestDTO pageRequest,
            CampaignStatus status,
            Long branchId,
            String creatorTier
    ) {
        campaignExpiryService.deactivateExpiredCampaigns();
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        UserModel currentUser = currentUserProvider.getCurrentUserOrThrow();
        UserRole currentRole = currentUserProvider.getCurrentUserRole();
        Specification<CampaignModel> specification = (root, ignored, cb) -> cb.conjunction();
        Long currentBranchId = null;

        if (canManageChainPromotions(currentRole)) {
            // No additional visibility restriction for chain-level roles.
        } else if (currentRole == UserRole.BRANCH_MANAGER) {
            currentBranchId = resolveCurrentBranchId(currentUser);
            Set<Long> visibleIds = campaignBranchVisibility.findVisibleCampaignIdsForBranch(currentBranchId);
            specification = visibleIds.isEmpty()
                    ? specification.and((root, ignored, cb) -> cb.disjunction())
                    : specification.and((root, ignored, cb) -> root.get("id").in(visibleIds));
        } else {
            throw new ForbiddenException("Access denied.");
        }

        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) ->
                    cb.like(cb.lower(root.get("name")), pattern));
        }
        if (status != null) {
            specification = specification.and((root, ignored, cb) -> cb.equal(root.get("status"), status));
        }
        if (branchId != null) {
            Set<Long> visibleIds = campaignBranchVisibility.findVisibleCampaignIdsForBranch(branchId);
            specification = visibleIds.isEmpty()
                    ? specification.and((root, ignored, cb) -> cb.disjunction())
                    : specification.and((root, ignored, cb) -> root.get("id").in(visibleIds));
        }
        if (creatorTier != null && !creatorTier.isBlank() && !"all".equalsIgnoreCase(creatorTier)) {
            Set<Long> chainCreatorIds = userRepository.findAll().stream()
                    .filter(user -> user.getRole() != null)
                    .filter(user -> {
                        UserRole role = user.getRole().toWebRole();
                        return role == UserRole.ADMIN || role == UserRole.DIRECTOR;
                    })
                    .map(UserModel::getId)
                    .collect(Collectors.toSet());
            if ("chain".equalsIgnoreCase(creatorTier)) {
                specification = chainCreatorIds.isEmpty()
                        ? specification.and((root, ignored, cb) -> cb.disjunction())
                        : specification.and((root, ignored, cb) -> root.get("createdBy").in(chainCreatorIds));
            } else if ("branch".equalsIgnoreCase(creatorTier) && !chainCreatorIds.isEmpty()) {
                specification = specification.and((root, ignored, cb) ->
                        cb.not(root.get("createdBy").in(chainCreatorIds)));
            }
        }

        Long responseBranchId = currentBranchId;
        Page<CampaignModel> campaigns = campaignRepository.findAll(
                specification,
                query.toPageable(
                        "createdAt",
                        Sort.Direction.DESC,
                        Set.of("id", "name", "status", "priority", "startAt", "endAt", "createdAt")));
        Map<Long, List<Long>> branchIdsByCampaign = loadBranchIdsByCampaign(campaigns.getContent());
        Set<Long> excludedCampaignIds = responseBranchId == null
                ? Set.of()
                : campaignBranchExclusionRepository.findByBranchId(responseBranchId).stream()
                .map(CampaignBranchExclusionModel::getCampaignId)
                .collect(Collectors.toSet());
        return campaigns.map(campaign -> {
            List<Long> campaignBranchIds = branchIdsByCampaign.getOrDefault(campaign.getId(), List.of());
            boolean deactivated = responseBranchId != null && isDeactivatedForBranch(
                    campaign, responseBranchId, campaignBranchIds, excludedCampaignIds);
            return campaignMapper.toSummaryResponse(campaign, campaignBranchIds, deactivated);
        });
    }

    private List<CampaignModel> findCampaignsVisibleToBranch(Long branchId) {
        return campaignBranchVisibility.findCampaignsVisibleToBranch(branchId);
    }

    private boolean isDeactivatedForBranch(
            CampaignModel campaign,
            Long branchId,
            List<Long> branchIds,
            Set<Long> excludedCampaignIds) {
        if (excludedCampaignIds.contains(campaign.getId())) {
            return true;
        }
        if (campaign.getScope() == CampaignScope.CHAIN && !branchIds.isEmpty()) {
            return !branchIds.contains(branchId);
        }
        return false;
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

        if (canManageChainPromotions(currentRole) && campaign.getScope() == CampaignScope.CHAIN) {
            return;
        }

        if (!currentUser.getId().equals(campaign.getCreatedBy())) {
            throw new ForbiddenException("Cannot modify promotions created by others.");
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
            CampaignType type = CampaignType.valueOf(normalized);
            if (type == CampaignType.BUY_X_GET_Y) {
                throw new BadRequestException("Buy X get Y promotions are no longer supported.");
            }
            return type;
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

    private void replaceCampaignBranches(Long campaignId, List<Long> branchIds) {
        List<Long> desired = branchIds == null ? List.of() : branchIds;
        List<CampaignBranchModel> existing = campaignBranchRepository.findByCampaignId(campaignId);
        java.util.Set<Long> existingIds = existing.stream()
                .map(CampaignBranchModel::getBranchId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        java.util.Set<Long> desiredIds = new java.util.LinkedHashSet<>(desired);

        List<CampaignBranchModel> toRemove = existing.stream()
                .filter(row -> !desiredIds.contains(row.getBranchId()))
                .toList();
        if (!toRemove.isEmpty()) {
            campaignBranchRepository.deleteAllInBatch(toRemove);
            campaignBranchRepository.flush();
        }

        List<Long> toAdd = desiredIds.stream()
                .filter(branchId -> !existingIds.contains(branchId))
                .toList();
        saveCampaignBranches(campaignId, toAdd);
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

    private String serializeConditions(JsonNode conditions) {
        if (conditions == null || conditions.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(normalizeJsonStrings(conditions));
        } catch (Exception ex) {
            throw new BadRequestException("Invalid promotion conditions.");
        }
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
