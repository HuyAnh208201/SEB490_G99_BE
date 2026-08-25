package base.api.feature.system.service.impl;

import base.api.feature.system.dto.request.UpdateMembershipTierRequest;
import base.api.feature.system.dto.response.MembershipTierResponse;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.feature.system.service.IMembershipTierService;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class MembershipTierServiceImpl implements IMembershipTierService {

    private static final Set<String> LEGACY_TIER_NAMES = Set.of("Đồng", "Bạc", "Vàng");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MembershipTierRepository membershipTierRepository;

    @Override
    @Transactional
    public List<MembershipTierResponse> listTiers() {
        deactivateLegacyTiers();
        return membershipTierRepository.findAllByOrderBySortOrderAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private void deactivateLegacyTiers() {
        for (MembershipTierModel tier : membershipTierRepository.findAllByOrderBySortOrderAsc()) {
            if (Boolean.TRUE.equals(tier.getActive()) && isLegacyTier(tier)) {
                tier.setActive(false);
                membershipTierRepository.save(tier);
            }
        }
    }

    private static boolean isLegacyTier(MembershipTierModel tier) {
        String code = tier.getCode();
        if (code == null || code.isBlank()) {
            return true;
        }
        String name = tier.getName();
        return name != null && LEGACY_TIER_NAMES.contains(name.trim());
    }

    @Override
    @Transactional
    public MembershipTierResponse updateTier(Long id, UpdateMembershipTierRequest request) {
        MembershipTierModel tier = membershipTierRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Membership tier not found."));

        Long minPoints = request.getMinPoints();
        Long maxPoints = request.getMaxPoints();
        if (minPoints == null || minPoints < 0) {
            throw new BadRequestException("minPoints must be >= 0.");
        }
        if (maxPoints != null && maxPoints < minPoints) {
            throw new BadRequestException("maxPoints must be >= minPoints (or null).");
        }
        if (request.getPointMultiplier() == null || request.getPointMultiplier() <= 0) {
            throw new BadRequestException("pointMultiplier must be > 0.");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("name is required.");
        }

        tier.setName(request.getName().trim());
        tier.setMinPoints(minPoints);
        tier.setMaxPoints(maxPoints);
        tier.setPointMultiplier(request.getPointMultiplier());
        tier.setBenefitsJson(writeBenefits(request.getBenefits()));
        tier.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        tier.setActive(Boolean.TRUE.equals(request.getActive()));

        MembershipTierModel saved = membershipTierRepository.save(tier);
        assertNoActiveRangeOverlap();
        return toResponse(saved);
    }

    private void assertNoActiveRangeOverlap() {
        List<MembershipTierModel> active = membershipTierRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(t -> Boolean.TRUE.equals(t.getActive()))
                .sorted(Comparator.comparing(MembershipTierModel::getMinPoints))
                .toList();

        for (int i = 0; i < active.size(); i++) {
            MembershipTierModel a = active.get(i);
            long aMin = a.getMinPoints() == null ? 0L : a.getMinPoints();
            Long aMax = a.getMaxPoints();
            for (int j = i + 1; j < active.size(); j++) {
                MembershipTierModel b = active.get(j);
                long bMin = b.getMinPoints() == null ? 0L : b.getMinPoints();
                Long bMax = b.getMaxPoints();
                if (rangesOverlap(aMin, aMax, bMin, bMax)) {
                    throw new BadRequestException(
                            "Active tiers \"" + a.getCode() + "\" and \"" + b.getCode()
                                    + "\" have overlapping point ranges.");
                }
            }
        }
    }

    /** Inclusive bounds; null max = +infinity. */
    private static boolean rangesOverlap(long aMin, Long aMax, long bMin, Long bMax) {
        long aHi = aMax == null ? Long.MAX_VALUE : aMax;
        long bHi = bMax == null ? Long.MAX_VALUE : bMax;
        return aMin <= bHi && bMin <= aHi;
    }

    private MembershipTierResponse toResponse(MembershipTierModel tier) {
        return new MembershipTierResponse(
                tier.getId(),
                tier.getCode(),
                tier.getName(),
                tier.getMinPoints(),
                tier.getMaxPoints(),
                tier.getPointMultiplier(),
                parseBenefits(tier.getBenefitsJson()),
                tier.getSortOrder(),
                tier.getActive()
        );
    }

    private List<String> parseBenefits(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<>() {
            });
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String writeBenefits(List<String> benefits) {
        List<String> cleaned = new ArrayList<>();
        if (benefits != null) {
            for (String b : benefits) {
                if (b != null && !b.isBlank()) {
                    cleaned.add(b.trim());
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new BadRequestException("Invalid benefits list.");
        }
    }
}
