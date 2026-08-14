package base.api.feature.customer.service;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.UserModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class CustomerTierService {

    private final MembershipTierRepository tierRepository;
    private final PointTransactionRepository pointRepository;
    private final IUserRepository userRepository;
    private final ObjectMapper objectMapper;

    public CustomerTierService(
            MembershipTierRepository tierRepository,
            PointTransactionRepository pointRepository,
            IUserRepository userRepository,
            ObjectMapper objectMapper) {
        this.tierRepository = tierRepository;
        this.pointRepository = pointRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public long lifetimeEarned(Long userId, Long balanceFallback) {
        Long sum = pointRepository.sumEarnedPoints(userId);
        return sum == null || sum == 0L ? defaultLong(balanceFallback) : sum;
    }

    @Transactional
    public MembershipTierModel syncUserTier(UserModel user) {
        MembershipTierModel tier = resolveTier(lifetimeEarned(user.getId(), user.getPoints()));
        if (tier != null && !Objects.equals(tier.getId(), user.getMembershipTierId())) {
            user.setMembershipTierId(tier.getId());
            userRepository.save(user);
        }
        return tier;
    }

    public List<MembershipTierModel> allActive() {
        return tierRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    public List<String> parseBenefits(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of(json);
        }
    }

    private MembershipTierModel resolveTier(long lifetimeEarned) {
        MembershipTierModel matched = null;
        List<MembershipTierModel> tiers = allActive();
        for (MembershipTierModel tier : tiers) {
            if (lifetimeEarned >= tier.getMinPoints()
                    && (tier.getMaxPoints() == null || lifetimeEarned <= tier.getMaxPoints())) {
                matched = tier;
            }
        }
        return matched == null && !tiers.isEmpty() ? tiers.get(0) : matched;
    }

    private long defaultLong(Long value) {
        return value == null ? 0L : value;
    }
}
