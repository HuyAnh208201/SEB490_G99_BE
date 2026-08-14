package base.api.feature.customer.service;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.customer.dto.CustomerDtos;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserGender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerAccountServiceTest {

    @Mock
    private IUserRepository userRepository;
    @Mock
    private PointTransactionRepository pointRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CustomerTierService tierService;

    private CustomerAccountService service;
    private UserModel user;

    @BeforeEach
    void setUp() {
        service = new CustomerAccountService(userRepository, pointRepository, orderRepository, tierService);
        user = new UserModel();
        user.setId(7L);
        user.setFullName("Customer One");
        user.setEmail("customer@example.com");
        user.setPhone("0912345678");
        user.setPoints(100L);
        user.setGender(UserGender.FEMALE);
        user.setBirthDate(LocalDate.of(1995, 5, 20).atStartOfDay());
        user.setCreatedAt(LocalDateTime.of(2024, 1, 2, 3, 4));
    }

    @Test
    void profileMapsPersistedCustomerAndTierFields() {
        MembershipTierModel tier = tier(1L, "SILVER", 0L, 2499L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tierService.syncUserTier(user)).thenReturn(tier);
        when(tierService.lifetimeEarned(7L, 100L)).thenReturn(120L);
        when(tierService.parseBenefits("[\"Support\"]")).thenReturn(List.of("Support"));

        CustomerDtos.ProfileResponse response = service.getProfile(user);

        assertEquals(7L, response.getId());
        assertEquals(LocalDate.of(1995, 5, 20), response.getDateOfBirth());
        assertEquals("FEMALE", response.getGender());
        assertEquals("SILVER", response.getTierCode());
        assertEquals(120L, response.getLifetimeEarnedPoints());
        assertEquals("0912345678", response.getQrPayload());
    }

    @Test
    void updateProfilePersistsSupportedMobileFields() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tierService.lifetimeEarned(any(), any())).thenReturn(100L);
        CustomerDtos.UpdateProfileRequest request = new CustomerDtos.UpdateProfileRequest();
        request.setFullName(" Updated Name ");
        request.setDateOfBirth(LocalDate.of(2000, 1, 1));
        request.setGender("male");

        CustomerDtos.ProfileResponse response = service.updateProfile(user, request);

        assertEquals("Updated Name", response.getFullName());
        assertEquals(UserGender.MALE, user.getGender());
        assertEquals(LocalDate.of(2000, 1, 1).atStartOfDay(), user.getBirthDate());
        verify(userRepository).save(user);
    }

    @Test
    void historyCombinesInvoicesAndRefundsThenPaginates() {
        OrderModel order = new OrderModel();
        order.setId(10L);
        order.setInvoiceCode("INV-10");
        order.setPointsEarned(8L);
        order.setTotal(new BigDecimal("85000"));
        order.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        PointTransactionModel refund = new PointTransactionModel();
        refund.setId(20L);
        refund.setOrderId(10L);
        refund.setPoints(-8L);
        refund.setCreatedAt(LocalDateTime.of(2026, 1, 2, 10, 0));
        when(orderRepository.findByCustomerIdOrderByCreatedAtDesc(any(), any()))
                .thenReturn(new PageImpl<>(List.of(order)));
        when(pointRepository.findByCustomerIdAndTypeIgnoreCaseOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(refund)));

        CustomerDtos.PageResponse<CustomerDtos.PointHistoryItem> response =
                service.pointHistory(7L, null, 0, 20);

        assertEquals(2, response.getTotalElements());
        assertEquals("REFUND_REVERSAL", response.getContent().get(0).getType());
        assertEquals("INVOICE", response.getContent().get(1).getType());
        assertEquals("Invoice INV-10", response.getContent().get(1).getLabel());
    }

    @Test
    void loyaltyTiersAndQrPreserveMobileShapes() {
        ReflectionTestUtils.setField(service, "vndPerPoint", 10_000L);
        ReflectionTestUtils.setField(service, "pointValueVnd", 1_000L);
        MembershipTierModel silver = tier(1L, "SILVER", 0L, 2499L);
        MembershipTierModel gold = tier(2L, "GOLD", 2500L, null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(tierService.syncUserTier(user)).thenReturn(silver);
        when(tierService.allActive()).thenReturn(List.of(silver, gold));
        when(tierService.parseBenefits(any())).thenReturn(List.of("Support"));

        CustomerDtos.LoyaltyConfigResponse config = service.loyaltyConfig();
        List<CustomerDtos.TierResponse> tiers = service.listTiers(user);
        CustomerDtos.QrResponse qr = service.qr(user);

        assertEquals(10_000L, config.getVndPerPoint());
        assertEquals(1_000L, config.getPointValueVnd());
        assertTrue(tiers.get(0).isCurrent());
        assertFalse(tiers.get(1).isCurrent());
        assertEquals("0912345678", qr.getPayload());
    }

    private MembershipTierModel tier(Long id, String code, Long min, Long max) {
        MembershipTierModel tier = new MembershipTierModel();
        tier.setId(id);
        tier.setCode(code);
        tier.setName(code.substring(0, 1) + code.substring(1).toLowerCase());
        tier.setMinPoints(min);
        tier.setMaxPoints(max);
        tier.setPointMultiplier(1.0);
        tier.setBenefitsJson("[\"Support\"]");
        tier.setSortOrder(id.intValue());
        tier.setActive(true);
        return tier;
    }
}
