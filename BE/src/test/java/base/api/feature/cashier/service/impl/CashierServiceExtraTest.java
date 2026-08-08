package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.dto.request.AddPointsRequest;
import base.api.feature.cashier.dto.response.AddPointsResponse;
import base.api.feature.cashier.dto.response.CustomerLookupResponse;
import base.api.feature.cashier.dto.response.LoyaltyConfigResponse;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.system.repository.MembershipTierRepository;
import base.api.shared.entity.MembershipTierModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra coverage for settlePoints, redeemValueOf, lookup, and loyalty config —
 * complementary to {@link CashierPointsTest} and {@link CashierCustomerTest}.
 */
@ExtendWith(MockitoExtension.class)
class CashierServiceExtraTest {

    private static final String PHONE = "0909123456";
    private static final String EMAIL = "guest@example.com";

    @Mock private IUserRepository userRepository;
    @Mock private IUserService userService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private MembershipTierRepository membershipTierRepository;

    @InjectMocks
    private CashierServiceImpl service;

    private UserModel customer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "vndPerPoint", 10_000L);
        ReflectionTestUtils.setField(service, "pointValueVnd", 1_000L);

        customer = new UserModel();
        customer.setId(7L);
        customer.setFullName("Tran Bao");
        customer.setPhone(PHONE);
        customer.setEmail(EMAIL);
        customer.setPoints(50L);
        customer.setRole(UserRole.CUSTOMER);
    }

    @Test
    void redeemValueOfZeroReturnsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.redeemValueOf(0)));
    }

    @Test
    void redeemValueOfNegativeReturnsZero() {
        assertEquals(0, BigDecimal.ZERO.compareTo(service.redeemValueOf(-3)));
    }

    @Test
    void redeemValueOfPositiveUsesConfiguredRate() {
        assertEquals(0, new BigDecimal("5000").compareTo(service.redeemValueOf(5)));
    }

    @Test
    void getLoyaltyConfigExposesConfiguredRates() {
        LoyaltyConfigResponse config = service.getLoyaltyConfig();

        assertEquals(10_000L, config.getVndPerPoint());
        assertEquals(1_000L, config.getPointValueVnd());
    }

    @Test
    void settlePointsEarnOnlyWhenRedeemIsZero() {
        ICashierService.PointSettlement settlement =
                service.settlePoints(customer, new BigDecimal("25000"), 0L);

        assertEquals(0L, settlement.pointsRedeemed());
        assertEquals(2L, settlement.pointsEarned());
        assertEquals(52L, settlement.totalPoints());
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
        verify(userRepository).refundPointsAtomic(7L, 2L);
    }

    @Test
    void settlePointsRedeemsThenEarns() {
        when(userRepository.deductPointsAtomic(7L, 10L)).thenReturn(1);

        ICashierService.PointSettlement settlement =
                service.settlePoints(customer, new BigDecimal("30000"), 10L);

        assertEquals(10L, settlement.pointsRedeemed());
        assertEquals(3L, settlement.pointsEarned());
        assertEquals(43L, settlement.totalPoints());
        verify(userRepository).deductPointsAtomic(7L, 10L);
        verify(userRepository).refundPointsAtomic(7L, 3L);
    }

    @Test
    void settlePointsRejectsInsufficientAtomicRedeem() {
        when(userRepository.deductPointsAtomic(7L, 99L)).thenReturn(0);

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.settlePoints(customer, new BigDecimal("50000"), 99L));

        assertTrue(error.getMessage().contains("enough points"));
        verify(userRepository, never()).refundPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void settlePointsNullInvoiceEarnsNothing() {
        ICashierService.PointSettlement settlement = service.settlePoints(customer, null, 0L);

        assertEquals(0L, settlement.pointsEarned());
        assertEquals(50L, settlement.totalPoints());
        verify(userRepository, never()).refundPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void settlePointsZeroEarnRateEarnsNothing() {
        ReflectionTestUtils.setField(service, "vndPerPoint", 0L);

        ICashierService.PointSettlement settlement =
                service.settlePoints(customer, new BigDecimal("999999"), 0L);

        assertEquals(0L, settlement.pointsEarned());
        verify(userRepository, never()).refundPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void lookupCustomerByPhone() {
        when(userRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(customer));

        CustomerLookupResponse result = service.lookupCustomer(PHONE);

        assertEquals(7L, result.getCustomerId());
        assertEquals(PHONE, result.getPhone());
        assertEquals(50L, result.getTotalPoints());
        assertNull(result.getTierCode());
    }

    @Test
    void lookupCustomerByEmailIncludesTier() {
        customer.setMembershipTierId(3L);
        MembershipTierModel tier = new MembershipTierModel();
        tier.setId(3L);
        tier.setCode("GOLD");
        tier.setName("Gold");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(customer));
        when(membershipTierRepository.findById(3L)).thenReturn(Optional.of(tier));

        CustomerLookupResponse result = service.lookupCustomer(EMAIL);

        assertEquals("GOLD", result.getTierCode());
        assertEquals("Gold", result.getTierName());
        verify(userRepository, never()).findByPhone(any());
    }

    @Test
    void lookupCustomerBlankFails() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.lookupCustomer("  "));

        assertTrue(error.getMessage().contains("Phone or email is required."));
    }

    @Test
    void lookupCustomerRejectsStaffAccount() {
        customer.setRole(UserRole.CASHIER);
        when(userRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(customer));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.lookupCustomer(PHONE));

        assertTrue(error.getMessage().contains("staff account"));
    }

    @Test
    void lookupCustomerNotFound() {
        when(userRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.lookupCustomer(PHONE));
    }

    @Test
    void addPointsFromInvoiceBlankKeyFails() {
        AddPointsRequest request = new AddPointsRequest();
        request.setPhoneOrEmail(" ");
        request.setInvoiceAmount(new BigDecimal("10000"));

        assertThrows(BadRequestException.class, () -> service.addPointsFromInvoice(request));
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void addPointsFromInvoiceViaEmailWritesRedeemHistory() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(customer));
        when(userRepository.deductPointsAtomic(7L, 2L)).thenReturn(1);

        AddPointsRequest request = new AddPointsRequest();
        request.setPhoneOrEmail(EMAIL);
        request.setInvoiceAmount(new BigDecimal("20000"));
        request.setPointsToRedeem(2L);

        AddPointsResponse result = service.addPointsFromInvoice(request);

        assertEquals(2L, result.getPointsRedeemed());
        assertEquals(2L, result.getPointsEarned());
        verify(pointTransactionRepository, org.mockito.Mockito.times(2)).save(any());
    }
}
