package base.api.feature.system.service.impl;

import base.api.feature.system.dto.request.UpdateMembershipTierRequest;
import base.api.feature.system.dto.response.MembershipTierResponse;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MembershipTierServiceImpl} update validation paths.
 */
@ExtendWith(MockitoExtension.class)
class MembershipTierServiceImplTest {

    @Mock
    private MembershipTierRepository membershipTierRepository;

    @InjectMocks
    private MembershipTierServiceImpl service;

    @Test
    void listTiersDeactivatesLegacyVietnameseAndEmptyCodeRows() {
        MembershipTierModel silver = tier(1L, "SILVER");
        MembershipTierModel legacyBronze = tier(2L, "");
        legacyBronze.setCode("");
        legacyBronze.setName("Đồng");
        legacyBronze.setActive(true);

        MembershipTierModel legacySilver = tier(3L, "LEGACY_SILVER");
        legacySilver.setName("Bạc");
        legacySilver.setActive(true);

        when(membershipTierRepository.findAllByOrderBySortOrderAsc())
                .thenReturn(List.of(silver, legacyBronze, legacySilver));
        when(membershipTierRepository.save(any(MembershipTierModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        List<MembershipTierResponse> responses = service.listTiers();

        assertEquals(3, responses.size());
        assertTrue(responses.stream().anyMatch(t -> "SILVER".equals(t.code()) && t.active()));
        assertTrue(responses.stream().anyMatch(t -> "Đồng".equals(t.name()) && !t.active()));
        assertTrue(responses.stream().anyMatch(t -> "Bạc".equals(t.name()) && !t.active()));
        verify(membershipTierRepository, times(2)).save(any(MembershipTierModel.class));
    }

    @Test
    void updateTierRejectsMissingTier() {
        when(membershipTierRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.updateTier(99L, validRequest()));

        assertEquals("Membership tier not found.", error.getMessage());
    }

    @Test
    void updateTierRejectsNegativeMinPoints() {
        when(membershipTierRepository.findById(1L)).thenReturn(Optional.of(tier(1L, "SILVER")));
        UpdateMembershipTierRequest request = validRequest();
        request.setMinPoints(-1L);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateTier(1L, request));

        assertEquals("minPoints must be >= 0.", error.getMessage());
    }

    @Test
    void updateTierRejectsMaxBelowMin() {
        when(membershipTierRepository.findById(1L)).thenReturn(Optional.of(tier(1L, "SILVER")));
        UpdateMembershipTierRequest request = validRequest();
        request.setMinPoints(100L);
        request.setMaxPoints(50L);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateTier(1L, request));

        assertEquals("maxPoints must be >= minPoints (or null).", error.getMessage());
    }

    @Test
    void updateTierRejectsNonPositiveMultiplier() {
        when(membershipTierRepository.findById(1L)).thenReturn(Optional.of(tier(1L, "SILVER")));
        UpdateMembershipTierRequest request = validRequest();
        request.setPointMultiplier(0.0);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateTier(1L, request));

        assertEquals("pointMultiplier must be > 0.", error.getMessage());
    }

    @Test
    void updateTierRejectsBlankName() {
        when(membershipTierRepository.findById(1L)).thenReturn(Optional.of(tier(1L, "SILVER")));
        UpdateMembershipTierRequest request = validRequest();
        request.setName("  ");

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateTier(1L, request));

        assertEquals("name is required.", error.getMessage());
        verify(membershipTierRepository, never()).save(any());
    }

    @Test
    void updateTierSucceedsAndReturnsResponse() {
        MembershipTierModel existing = tier(1L, "SILVER");
        when(membershipTierRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(membershipTierRepository.save(any(MembershipTierModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipTierRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(existing));

        MembershipTierResponse response = service.updateTier(1L, validRequest());

        assertEquals("Silver Plus", response.name());
        assertEquals(100L, response.minPoints());
        assertEquals(1.5, response.pointMultiplier());
        assertEquals("SILVER", response.code());
        verify(membershipTierRepository).save(existing);
    }

    @Test
    void updateTierRejectsOverlappingActiveRanges() {
        MembershipTierModel silver = tier(1L, "SILVER");
        silver.setMinPoints(0L);
        silver.setMaxPoints(200L);
        silver.setActive(true);

        MembershipTierModel gold = tier(2L, "GOLD");
        gold.setMinPoints(150L);
        gold.setMaxPoints(500L);
        gold.setActive(true);

        when(membershipTierRepository.findById(1L)).thenReturn(Optional.of(silver));
        when(membershipTierRepository.save(any(MembershipTierModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(membershipTierRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(silver, gold));

        UpdateMembershipTierRequest request = validRequest();
        request.setMinPoints(0L);
        request.setMaxPoints(200L);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateTier(1L, request));

        assertTrue(error.getMessage().contains("overlapping point ranges"));
        assertTrue(error.getMessage().contains("SILVER"));
        assertTrue(error.getMessage().contains("GOLD"));
    }

    private static UpdateMembershipTierRequest validRequest() {
        UpdateMembershipTierRequest request = new UpdateMembershipTierRequest();
        request.setName("Silver Plus");
        request.setMinPoints(100L);
        request.setMaxPoints(499L);
        request.setPointMultiplier(1.5);
        request.setBenefits(List.of("Free delivery"));
        request.setSortOrder(1);
        request.setActive(true);
        return request;
    }

    private static MembershipTierModel tier(Long id, String code) {
        MembershipTierModel tier = new MembershipTierModel();
        tier.setId(id);
        tier.setCode(code);
        tier.setName(code);
        tier.setMinPoints(0L);
        tier.setMaxPoints(100L);
        tier.setPointMultiplier(1.0);
        tier.setSortOrder(0);
        tier.setActive(true);
        return tier;
    }
}
