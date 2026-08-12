package base.api.feature.promotion.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.promotion.dto.request.ActivateCampaignRequest;
import base.api.feature.promotion.dto.request.CreateCampaignRequest;
import base.api.feature.promotion.dto.request.UpdateCampaignRequest;
import base.api.feature.promotion.dto.response.CampaignResponse;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.feature.promotion.mapper.CampaignMapper;
import base.api.feature.promotion.repository.CampaignBranchExclusionRepository;
import base.api.feature.promotion.repository.CampaignBranchRepository;
import base.api.feature.promotion.repository.CampaignRepository;
import base.api.feature.promotion.service.CampaignExpiryService;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.CampaignBranchExclusionModel;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CampaignServiceImpl} create / activate / suspend paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CampaignServiceImplTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignBranchRepository campaignBranchRepository;
    @Mock private CampaignBranchExclusionRepository campaignBranchExclusionRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private IUserRepository userRepository;
    @Mock private CampaignMapper campaignMapper;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ObjectMapper objectMapper;
    @Mock private CampaignExpiryService campaignExpiryService;

    @InjectMocks
    private CampaignServiceImpl service;

    @Test
    void createCampaignAsAdminSavesChainPromotion() {
        asAdmin();
        CreateCampaignRequest request = createRequest("Summer Sale", "CHAIN", "PERCENT");
        when(campaignRepository.existsByNameIgnoreCase("Summer Sale")).thenReturn(false);
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> {
            CampaignModel saved = inv.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        service.createCampaign(request);

        ArgumentCaptor<CampaignModel> captor = ArgumentCaptor.forClass(CampaignModel.class);
        verify(campaignRepository).save(captor.capture());
        assertEquals(CampaignScope.CHAIN, captor.getValue().getScope());
        assertEquals(CampaignStatus.DEACTIVATED, captor.getValue().getStatus());
        assertEquals(CampaignType.PERCENT, captor.getValue().getType());
    }

    @Test
    void createCampaignRejectsAdminCreatingBranchScope() {
        asAdmin();
        CreateCampaignRequest request = createRequest("Local Sale", "BRANCH", "PERCENT");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createCampaign(request));

        assertEquals("Administrator and promotion director can only create chain promotions.", error.getMessage());
    }

    @Test
    void createCampaignRejectsBranchManagerCreatingChainScope() {
        asBranchManager(5L);
        CreateCampaignRequest request = createRequest("Chain Sale", "CHAIN", "PERCENT");

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.createCampaign(request));

        assertEquals("Branch manager cannot create chain promotions.", error.getMessage());
    }

    @Test
    void createCampaignAsBranchManagerSavesBranchPromotion() {
        asBranchManager(5L);
        CreateCampaignRequest request = createRequest("Store Sale", "BRANCH", "FIXED_AMOUNT");
        when(campaignRepository.existsByNameIgnoreCase("Store Sale")).thenReturn(false);
        BranchModel branch = new BranchModel();
        branch.setId(5L);
        when(branchRepository.findAllById(List.of(5L))).thenReturn(List.of(branch));
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> {
            CampaignModel saved = inv.getArgument(0);
            saved.setId(2L);
            return saved;
        });
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        service.createCampaign(request);

        ArgumentCaptor<CampaignModel> captor = ArgumentCaptor.forClass(CampaignModel.class);
        verify(campaignRepository).save(captor.capture());
        assertEquals(CampaignScope.BRANCH, captor.getValue().getScope());
        verify(campaignBranchRepository).saveAll(anyList());
    }

    @Test
    void createCampaignRejectsBlankName() {
        asAdmin();
        CreateCampaignRequest request = createRequest("  ", "CHAIN", "PERCENT");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createCampaign(request));

        assertEquals("Promotion name is required.", error.getMessage());
    }

    @Test
    void createCampaignRejectsDuplicateName() {
        asAdmin();
        when(campaignRepository.existsByNameIgnoreCase("Summer Sale")).thenReturn(true);

        ConflictException error = assertThrows(
                ConflictException.class,
                () -> service.createCampaign(createRequest("Summer Sale", "CHAIN", "PERCENT")));

        assertEquals("Promotion already exists.", error.getMessage());
    }

    @Test
    void activateCampaignRejectsAlreadyActive() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.activateCampaign(1L));

        assertEquals("Promotion is already active.", error.getMessage());
    }

    @Test
    void activateCampaignRequiresNewDatesWhenPast() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        campaign.setStartAt(LocalDateTime.now().minusDays(10));
        campaign.setEndAt(LocalDateTime.now().minusDays(1));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.activateCampaign(1L));

        assertTrue(error.getMessage().contains("Provide a new startAt and endAt"));
    }

    @Test
    void activateCampaignSucceedsWithFutureDates() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        campaign.setStartAt(LocalDateTime.now().plusDays(1));
        campaign.setEndAt(LocalDateTime.now().plusDays(10));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        service.activateCampaign(1L);

        assertEquals(CampaignStatus.ACTIVE, campaign.getStatus());
        verify(campaignRepository).save(campaign);
    }

    @Test
    void activateCampaignWithNewDatesRejectsStartInPast() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        campaign.setStartAt(LocalDateTime.now().minusDays(5));
        campaign.setEndAt(LocalDateTime.now().minusDays(1));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        ActivateCampaignRequest request = new ActivateCampaignRequest();
        request.setStartAt(LocalDateTime.now().minusDays(1));
        request.setEndAt(LocalDateTime.now().plusDays(5));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.activateCampaign(1L, request));

        assertEquals("New start date must be today or later.", error.getMessage());
    }

    @Test
    void activateCampaignWithNewDatesPersistsDatesWhenEntityExpired() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        campaign.setStartAt(LocalDateTime.now().minusDays(10));
        campaign.setEndAt(LocalDateTime.now().minusDays(1));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        LocalDateTime newStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime newEnd = LocalDateTime.now().plusDays(7).withHour(23).withMinute(59).withSecond(0).withNano(0);
        ActivateCampaignRequest request = new ActivateCampaignRequest();
        request.setStartAt(newStart);
        request.setEndAt(newEnd);

        service.activateCampaign(1L, request);

        assertEquals(CampaignStatus.ACTIVE, campaign.getStatus());
        assertEquals(newStart, campaign.getStartAt());
        assertEquals(newEnd, campaign.getEndAt());
        verify(campaignRepository).save(campaign);
    }

    @Test
    void activateCampaignWithNewDatesRejectsEndAlreadyPast() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        campaign.setStartAt(LocalDateTime.now().minusDays(5));
        campaign.setEndAt(LocalDateTime.now().minusDays(1));
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        ActivateCampaignRequest request = new ActivateCampaignRequest();
        request.setStartAt(LocalDateTime.now().toLocalDate().atStartOfDay());
        request.setEndAt(LocalDateTime.now().minusMinutes(1));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.activateCampaign(1L, request));

        assertEquals("New end date must be in the future.", error.getMessage());
    }

    @Test
    void suspendCampaignRejectsNonActive() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.suspendCampaign(1L));

        assertEquals("Promotion is already deactivated.", error.getMessage());
    }

    @Test
    void suspendCampaignDeactivatesActivePromotion() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        service.suspendCampaign(1L);

        assertEquals(CampaignStatus.DEACTIVATED, campaign.getStatus());
    }

    @Test
    void createCampaignHonorsRequestedDraftStatus() {
        asAdmin();
        CreateCampaignRequest request = createRequest("Draft Sale", "CHAIN", "PERCENT");
        request.setStatus("DRAFT");
        when(campaignRepository.existsByNameIgnoreCase("Draft Sale")).thenReturn(false);
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> {
            CampaignModel saved = inv.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        service.createCampaign(request);

        ArgumentCaptor<CampaignModel> captor = ArgumentCaptor.forClass(CampaignModel.class);
        verify(campaignRepository).save(captor.capture());
        assertEquals(CampaignStatus.DRAFT, captor.getValue().getStatus());
    }

    @Test
    void createCampaignRejectsCreatingDirectlyAsActive() {
        asAdmin();
        CreateCampaignRequest request = createRequest("Instant Sale", "CHAIN", "PERCENT");
        request.setStatus("ACTIVE");

        BadRequestException error =
                assertThrows(BadRequestException.class, () -> service.createCampaign(request));

        // Creating straight into ACTIVE would bypass the checks inside activateCampaign.
        assertTrue(error.getMessage().contains("DRAFT or DEACTIVATED"));
        verify(campaignRepository, never()).save(any(CampaignModel.class));
    }

    @Test
    void createCampaignRejectsStartDateInThePast() {
        asAdmin();
        CreateCampaignRequest request = createRequest("Past Sale", "CHAIN", "PERCENT");
        request.setStartAt(LocalDateTime.now().minusDays(3));
        request.setEndAt(LocalDateTime.now().plusDays(3));

        BadRequestException error =
                assertThrows(BadRequestException.class, () -> service.createCampaign(request));

        assertEquals("Start date must be today or later.", error.getMessage());
        verify(campaignRepository, never()).save(any(CampaignModel.class));
    }

    @Test
    void deleteCampaignSucceedsForDeactivatedPromotion() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        service.deleteCampaign(1L);

        verify(campaignBranchRepository).deleteByCampaignId(1L);
        verify(campaignBranchExclusionRepository).deleteByCampaignId(1L);
        verify(campaignRepository).delete(campaign);
    }

    @Test
    void updateCampaignRejectsActivePromotion() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateCampaign(1L, updateRequest("Renamed")));

        assertTrue(error.getMessage().contains("cannot be edited"));
        assertEquals("Campaign 1", campaign.getName());
        verify(campaignRepository, never()).save(any(CampaignModel.class));
    }

    @Test
    void updateCampaignSucceedsForDeactivatedPromotion() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.existsByNameIgnoreCase("Renamed")).thenReturn(false);
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        service.updateCampaign(1L, updateRequest("Renamed"));

        assertEquals("Renamed", campaign.getName());
    }

    @Test
    void getCampaignThrowsWhenMissing() {
        when(campaignRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.getCampaign(99L));

        assertEquals("Promotion not found.", error.getMessage());
    }

    @Test
    void updateCampaignAsAdminReplacesBranchMappings() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.existsByNameIgnoreCaseAndIdNot("Summer Sale", 1L)).thenReturn(false);
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        BranchModel b1 = new BranchModel();
        b1.setId(10L);
        BranchModel b2 = new BranchModel();
        b2.setId(20L);
        when(branchRepository.findAllById(List.of(10L, 20L))).thenReturn(List.of(b1, b2));
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        UpdateCampaignRequest request = new UpdateCampaignRequest();
        request.setName("Summer Sale");
        request.setType("PERCENT");
        request.setDiscountValue(new BigDecimal("10"));
        request.setPriority(1);
        request.setStartAt(LocalDateTime.now().plusDays(1));
        request.setEndAt(LocalDateTime.now().plusDays(30));
        request.setScope("CHAIN");
        request.setBranchIds(List.of(10L, 20L));

        service.updateCampaign(1L, request);

        verify(campaignBranchRepository).saveAll(anyList());
    }

    @Test
    void updateCampaignAsAdminClearsBranchesForEntireChain() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.DEACTIVATED, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignRepository.existsByNameIgnoreCaseAndIdNot("Summer Sale", 1L)).thenReturn(false);
        when(campaignRepository.save(any(CampaignModel.class))).thenAnswer(inv -> inv.getArgument(0));
        CampaignBranchModel existing = new CampaignBranchModel();
        existing.setId(99L);
        existing.setCampaignId(1L);
        existing.setBranchId(10L);
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of(existing));
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList()))
                .thenReturn(new CampaignResponse());

        UpdateCampaignRequest request = new UpdateCampaignRequest();
        request.setName("Summer Sale");
        request.setType("PERCENT");
        request.setDiscountValue(new BigDecimal("10"));
        request.setPriority(1);
        request.setStartAt(LocalDateTime.now().plusDays(1));
        request.setEndAt(LocalDateTime.now().plusDays(30));
        request.setScope("CHAIN");
        request.setBranchIds(List.of());

        service.updateCampaign(1L, request);

        verify(campaignBranchRepository).deleteAllInBatch(anyList());
        verify(campaignBranchRepository).flush();
    }

    @Test
    void deleteCampaignRejectsActivePromotion() {
        asAdmin();
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.deleteCampaign(1L));

        assertEquals("Active promotions cannot be deleted. Deactivate the promotion first.", error.getMessage());
        verify(campaignRepository, never()).delete(any(CampaignModel.class));
        verify(campaignBranchRepository, never()).deleteByCampaignId(1L);
        verify(campaignBranchExclusionRepository, never()).deleteByCampaignId(1L);
    }

    @Test
    void createCampaignRejectsBuyXGetY() {
        asAdmin();
        CreateCampaignRequest request = createRequest("BXGY", "CHAIN", "BUY_X_GET_Y");

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createCampaign(request));

        assertEquals("Buy X get Y promotions are no longer supported.", error.getMessage());
    }

    // ─── Enabling and disabling a chain campaign for one branch ─────────────────
    // The deactivate/activate pair must stay symmetric: one writes an exclusion row, the
    // other removes it. These tests pin both directions for campaigns with and without an
    // explicit branch list, because those two cases take different paths.

    @Test
    void deactivateForBranchWritesExclusionAndKeepsBranchMapping() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(branchMappings(1L, 5L, 6L));
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, 5L)).thenReturn(false);
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList())).thenReturn(new CampaignResponse());

        service.deactivateCampaignForBranch(1L);

        ArgumentCaptor<CampaignBranchExclusionModel> captor =
                ArgumentCaptor.forClass(CampaignBranchExclusionModel.class);
        verify(campaignBranchExclusionRepository).save(captor.capture());
        assertEquals(1L, captor.getValue().getCampaignId());
        assertEquals(5L, captor.getValue().getBranchId());
        // The branch list is the Admin's configuration — opting one branch out must not touch it.
        verify(campaignBranchRepository, never()).delete(any(CampaignBranchModel.class));
    }

    @Test
    void deactivateForBranchWritesExclusionWhenCampaignTargetsNoBranch() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, 5L)).thenReturn(false);
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList())).thenReturn(new CampaignResponse());

        service.deactivateCampaignForBranch(1L);

        verify(campaignBranchExclusionRepository).save(any(CampaignBranchExclusionModel.class));
    }

    @Test
    void activateForBranchRemovesExclusionWhenCampaignTargetsBranches() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(branchMappings(1L, 5L, 6L));
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, 5L)).thenReturn(true);
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList())).thenReturn(new CampaignResponse());

        service.activateCampaignForBranch(1L);

        verify(campaignBranchExclusionRepository).deleteByCampaignIdAndBranchId(1L, 5L);
    }

    @Test
    void activateForBranchRemovesExclusionWhenCampaignTargetsNoBranch() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(List.of());
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, 5L)).thenReturn(true);
        when(campaignMapper.toResponse(any(CampaignModel.class), anyList())).thenReturn(new CampaignResponse());

        service.activateCampaignForBranch(1L);

        verify(campaignBranchExclusionRepository).deleteByCampaignIdAndBranchId(1L, 5L);
    }

    @Test
    void deactivateForBranchRejectsSecondCall() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(branchMappings(1L, 5L));
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, 5L)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class,
                () -> service.deactivateCampaignForBranch(1L));

        assertEquals("Promotion already deactivated for this branch.", error.getMessage());
        verify(campaignBranchExclusionRepository, never()).save(any(CampaignBranchExclusionModel.class));
    }

    @Test
    void activateForBranchRejectsWhenNotDeactivated() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(branchMappings(1L, 5L));
        when(campaignBranchExclusionRepository.existsByCampaignIdAndBranchId(1L, 5L)).thenReturn(false);

        BadRequestException error = assertThrows(BadRequestException.class,
                () -> service.activateCampaignForBranch(1L));

        assertEquals("Promotion is not deactivated for this branch.", error.getMessage());
        verify(campaignBranchExclusionRepository, never()).deleteByCampaignIdAndBranchId(1L, 5L);
    }

    @Test
    void deactivateForBranchRejectsBranchOutsideCampaignTargets() {
        asBranchManager(9L);
        stubBranchExists(9L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));
        when(campaignBranchRepository.findByCampaignId(1L)).thenReturn(branchMappings(1L, 5L, 6L));

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.deactivateCampaignForBranch(1L));

        assertEquals("Promotion is not applied to this branch.", error.getMessage());
        verify(campaignBranchExclusionRepository, never()).save(any(CampaignBranchExclusionModel.class));
    }

    @Test
    void deactivateForBranchRejectsNonBranchManager() {
        asAdmin();

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.deactivateCampaignForBranch(1L));

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void activateForBranchRejectsBranchScopedCampaign() {
        asBranchManager(5L);
        stubBranchExists(5L);
        CampaignModel campaign = campaign(1L, CampaignStatus.ACTIVE, CampaignScope.BRANCH);
        when(campaignRepository.findById(1L)).thenReturn(Optional.of(campaign));

        ForbiddenException error = assertThrows(ForbiddenException.class,
                () -> service.activateCampaignForBranch(1L));

        assertEquals("Cannot activate branch promotions with this action.", error.getMessage());
    }

    // -------------------------------------------------------------------------
    // getApplicableForBranch — the single source deciding which campaigns reach the counter
    // -------------------------------------------------------------------------

    @Test
    void applicableForBranchKeepsLiveChainCampaignWithNoBranchTargeting() {
        CampaignModel live = liveCampaign(1L, 0);
        stubVisibleToBranch(List.of(live));
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of());
        stubSummaryMapper();

        List<CampaignSummaryResponse> result = service.getApplicableForBranch(7L);

        assertEquals(List.of(1L), ids(result));
        verify(campaignExpiryService).deactivateExpiredCampaigns();
    }

    @Test
    void applicableForBranchDropsCampaignOutsideItsDateWindow() {
        CampaignModel expired = liveCampaign(1L, 0);
        expired.setEndAt(LocalDateTime.now().minusMinutes(1));
        CampaignModel notStarted = liveCampaign(2L, 0);
        notStarted.setStartAt(LocalDateTime.now().plusDays(1));
        stubVisibleToBranch(List.of(expired, notStarted));
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of());
        stubSummaryMapper();

        assertTrue(service.getApplicableForBranch(7L).isEmpty());
    }

    @Test
    void applicableForBranchDropsCampaignThatIsNotActive() {
        CampaignModel draft = liveCampaign(1L, 0);
        draft.setStatus(CampaignStatus.DRAFT);
        stubVisibleToBranch(List.of(draft));
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of());
        stubSummaryMapper();

        assertTrue(service.getApplicableForBranch(7L).isEmpty());
    }

    @Test
    void applicableForBranchDropsCampaignTheBranchOptedOutOf() {
        stubVisibleToBranch(List.of(liveCampaign(1L, 0)));
        CampaignBranchExclusionModel exclusion = new CampaignBranchExclusionModel();
        exclusion.setCampaignId(1L);
        exclusion.setBranchId(7L);
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of(exclusion));
        stubSummaryMapper();

        assertTrue(service.getApplicableForBranch(7L).isEmpty());
    }

    @Test
    void applicableForBranchDropsChainCampaignAimedAtOtherBranches() {
        stubVisibleToBranch(List.of(liveCampaign(1L, 0)));
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of());
        when(campaignBranchRepository.findByCampaignIdIn(anyList()))
                .thenReturn(branchMappings(1L, 2L, 3L));
        stubSummaryMapper();

        assertTrue(service.getApplicableForBranch(7L).isEmpty());
    }

    /**
     * Found on live data: a BRANCH-scoped campaign owned by branch 5 reached the counter at
     * branch 1. isDeactivatedForBranch only inspects the branch list when the scope is CHAIN,
     * so the input set has to be filtered first by findCampaignsVisibleToBranch.
     */
    @Test
    void applicableForBranchDropsBranchScopedCampaignOwnedByAnotherBranch() {
        CampaignModel otherBranchPromo = liveCampaign(9L, 0);
        otherBranchPromo.setScope(CampaignScope.BRANCH);
        // Branch 7 has no mapping to campaign 9, so it must not see it.
        when(campaignRepository.findByScopeOrderByIdAsc(CampaignScope.CHAIN)).thenReturn(List.of());
        when(campaignBranchRepository.findCampaignIdsByBranchId(7L)).thenReturn(List.of());
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of());
        stubSummaryMapper();

        assertTrue(service.getApplicableForBranch(7L).isEmpty());
        verify(campaignRepository, never()).findByIdIn(anyList());
    }

    /** Apply order decides the money, so it is a contract rather than an implementation detail. */
    @Test
    void applicableForBranchSortsByPriorityThenId() {
        stubVisibleToBranch(List.of(
                liveCampaign(5L, 0),
                liveCampaign(3L, 9),
                liveCampaign(2L, 0),
                liveCampaign(8L, 9)));
        when(campaignBranchExclusionRepository.findByBranchId(7L)).thenReturn(List.of());
        stubSummaryMapper();

        assertEquals(List.of(3L, 8L, 2L, 5L), ids(service.getApplicableForBranch(7L)));
    }

    /**
     * Builds exactly the set findCampaignsVisibleToBranch returns: chain campaigns plus the
     * ones mapped to the branch. Every test goes through here, because this is the real
     * input set for getApplicableForBranch.
     */
    private void stubVisibleToBranch(List<CampaignModel> campaigns) {
        when(campaignRepository.findByScopeOrderByIdAsc(CampaignScope.CHAIN)).thenReturn(campaigns);
        when(campaignBranchRepository.findCampaignIdsByBranchId(anyLong())).thenReturn(List.of());
    }

    private static List<Long> ids(List<CampaignSummaryResponse> rows) {
        return rows.stream().map(CampaignSummaryResponse::getId).toList();
    }

    /** An ACTIVE campaign inside its dates — the default state for the tests below. */
    private static CampaignModel liveCampaign(Long id, int priority) {
        CampaignModel campaign = campaign(id, CampaignStatus.ACTIVE, CampaignScope.CHAIN);
        campaign.setPriority(priority);
        campaign.setStartAt(LocalDateTime.now().minusDays(1));
        campaign.setEndAt(LocalDateTime.now().plusDays(1));
        return campaign;
    }

    private void stubSummaryMapper() {
        when(campaignMapper.toSummaryResponse(any(CampaignModel.class), anyList()))
                .thenAnswer(inv -> {
                    CampaignModel source = inv.getArgument(0);
                    CampaignSummaryResponse response = new CampaignSummaryResponse();
                    response.setId(source.getId());
                    response.setName(source.getName());
                    return response;
                });
    }

    private void stubBranchExists(Long branchId) {
        BranchModel branch = new BranchModel();
        branch.setId(branchId);
        when(branchRepository.findAllById(List.of(branchId))).thenReturn(List.of(branch));
    }

    private static List<CampaignBranchModel> branchMappings(Long campaignId, Long... branchIds) {
        return java.util.Arrays.stream(branchIds)
                .map(branchId -> {
                    CampaignBranchModel mapping = new CampaignBranchModel();
                    mapping.setCampaignId(campaignId);
                    mapping.setBranchId(branchId);
                    return mapping;
                })
                .toList();
    }

    private void asAdmin() {
        UserModel user = new UserModel();
        user.setId(1L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
    }

    private void asBranchManager(Long branchId) {
        UserModel user = new UserModel();
        user.setId(2L);
        user.setBranchId(branchId);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
    }

    private static UpdateCampaignRequest updateRequest(String name) {
        UpdateCampaignRequest request = new UpdateCampaignRequest();
        request.setName(name);
        request.setType("PERCENT");
        request.setDiscountValue(new BigDecimal("15"));
        request.setPriority(2);
        request.setStartAt(LocalDateTime.now().plusDays(1));
        request.setEndAt(LocalDateTime.now().plusDays(10));
        return request;
    }

    private static CreateCampaignRequest createRequest(String name, String scope, String type) {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setName(name);
        request.setScope(scope);
        request.setType(type);
        request.setDiscountValue(new BigDecimal("10"));
        request.setPriority(1);
        request.setStartAt(LocalDateTime.now().plusDays(1));
        request.setEndAt(LocalDateTime.now().plusDays(30));
        return request;
    }

    private static CampaignModel campaign(Long id, CampaignStatus status, CampaignScope scope) {
        CampaignModel campaign = new CampaignModel();
        campaign.setId(id);
        campaign.setName("Campaign " + id);
        campaign.setStatus(status);
        campaign.setScope(scope);
        campaign.setType(CampaignType.PERCENT);
        campaign.setCreatedBy(1L);
        return campaign;
    }
}
