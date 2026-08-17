package base.api.feature.customer.service;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.UserModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerTierServiceTest {

    @Mock
    private MembershipTierRepository tierRepository;
    @Mock
    private PointTransactionRepository pointRepository;
    @Mock
    private IUserRepository userRepository;

    private CustomerTierService service;

    @BeforeEach
    void setUp() {
        service = new CustomerTierService(tierRepository, pointRepository, userRepository, new ObjectMapper());
    }

    @Test
    void resolvesLifetimeTierAndSynchronizesUser() {
        UserModel user = new UserModel();
        user.setId(7L);
        user.setPoints(100L);
        MembershipTierModel silver = tier(1L, 0L, 2499L);
        MembershipTierModel gold = tier(2L, 2500L, 5499L);
        when(pointRepository.sumEarnedPoints(7L)).thenReturn(3000L);
        when(tierRepository.findByActiveTrueOrderBySortOrderAsc()).thenReturn(List.of(silver, gold));

        MembershipTierModel result = service.syncUserTier(user);

        assertEquals(2L, result.getId());
        assertEquals(2L, user.getMembershipTierId());
        verify(userRepository).save(user);
    }

    @Test
    void usesBalanceFallbackAndParsesBenefitsSafely() {
        when(pointRepository.sumEarnedPoints(7L)).thenReturn(0L);

        assertEquals(50L, service.lifetimeEarned(7L, 50L));
        assertEquals(List.of("One", "Two"), service.parseBenefits("[\"One\",\"Two\"]"));
        assertEquals(List.of("not-json"), service.parseBenefits("not-json"));
        assertEquals(List.of(), service.parseBenefits(null));
    }

    private MembershipTierModel tier(Long id, Long min, Long max) {
        MembershipTierModel tier = new MembershipTierModel();
        tier.setId(id);
        tier.setMinPoints(min);
        tier.setMaxPoints(max);
        tier.setActive(true);
        return tier;
    }
}
