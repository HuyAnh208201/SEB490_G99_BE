package base.api.feature.cashier.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.cashier.dto.request.RedeemVoucherRequest;
import base.api.feature.cashier.dto.response.RedeemVoucherResponse;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.voucher.service.VoucherCodeGenerator;
import base.api.shared.entity.PointTransactionModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spending points on a voucher code at the counter. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CashierVoucherRedeemTest {

    private static final Long CUSTOMER_ID = 10L;
    private static final Long CATALOG_ID = 21L;
    private static final String PHONE = "0911111111";

    @Mock private IUserRepository userRepository;
    @Mock private base.api.feature.auth.service.IUserService userService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private base.api.feature.system.repository.MembershipTierRepository membershipTierRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherCatalogRepository voucherCatalogRepository;
    @Mock private VoucherCodeGenerator voucherCodeGenerator;

    @InjectMocks
    private CashierServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "voucherExpiryDays", 30L);
        when(userRepository.findByEmail(PHONE)).thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(customer(500L)));
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog(100)));
        when(voucherCodeGenerator.generate(anyString())).thenReturn("VCABCDEFGH");
        when(voucherRepository.save(any())).thenAnswer(call -> {
            VoucherModel voucher = call.getArgument(0);
            if (voucher.getId() == null) {
                voucher.setId(77L);
            }
            return voucher;
        });
        when(userRepository.deductPointsAtomic(anyLong(), anyLong())).thenReturn(1);
        // The returned balance is re-read after the deduction, not taken from the loaded entity.
        when(userRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(400L)));
    }

    @Test
    void redeemingDeductsPointsAndIssuesCodeForThatCustomer() {
        RedeemVoucherResponse response = service.redeemVoucher(request());

        verify(userRepository).deductPointsAtomic(CUSTOMER_ID, 100L);
        ArgumentCaptor<VoucherModel> saved = ArgumentCaptor.forClass(VoucherModel.class);
        verify(voucherRepository).save(saved.capture());
        // Must be users.id — the same id space checkout uses, otherwise the code just issued
        // would be refused as "belongs to another customer".
        assertEquals(CUSTOMER_ID, saved.getValue().getCustomerId());
        assertEquals("active", saved.getValue().getStatus());
        assertEquals("VCABCDEFGH", response.getCode());
        assertEquals(100L, response.getPointsSpent());
        assertEquals(400L, response.getPointsRemaining());
    }

    @Test
    void redeemingRecordsANegativePointTransaction() {
        service.redeemVoucher(request());

        ArgumentCaptor<PointTransactionModel> tx = ArgumentCaptor.forClass(PointTransactionModel.class);
        verify(pointTransactionRepository).save(tx.capture());
        assertEquals(-100L, tx.getValue().getPoints());
        assertEquals("VOUCHER_REDEEM", tx.getValue().getType());
        assertEquals(CUSTOMER_ID, tx.getValue().getCustomerId());
    }

    @Test
    void expiryComesFromConfiguredWindow() {
        LocalDateTime before = LocalDateTime.now().plusDays(30);

        RedeemVoucherResponse response = service.redeemVoucher(request());

        assertTrue(response.getExpiresAt().isAfter(before.minusMinutes(1)));
        assertTrue(response.getExpiresAt().isBefore(before.plusMinutes(1)));
    }

    /** deductPointsAtomic matching zero rows means not enough points — issue nothing. */
    @Test
    void notEnoughPointsIssuesNothing() {
        when(userRepository.deductPointsAtomic(anyLong(), anyLong())).thenReturn(0);

        BadRequestException error =
                assertThrows(BadRequestException.class, () -> service.redeemVoucher(request()));

        assertTrue(error.getMessage().contains("enough points"));
        verify(voucherRepository, never()).save(any());
        verify(pointTransactionRepository, never()).save(any());
    }

    @Test
    void inactiveCatalogCannotBeRedeemed() {
        VoucherCatalogModel catalog = catalog(100);
        catalog.setStatus("inactive");
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog));

        assertThrows(BadRequestException.class, () -> service.redeemVoucher(request()));
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
    }

    /** points_required = 0 means the type is issued by hand only, never bought with points. */
    @Test
    void catalogWithoutPointsPriceCannotBeRedeemed() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.of(catalog(0)));

        BadRequestException error =
                assertThrows(BadRequestException.class, () -> service.redeemVoucher(request()));

        assertTrue(error.getMessage().contains("cannot be redeemed with points"));
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void unknownCatalogIsRejected() {
        when(voucherCatalogRepository.findById(CATALOG_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.redeemVoucher(request()));
        verify(userRepository, never()).deductPointsAtomic(anyLong(), anyLong());
    }

    @Test
    void unknownCustomerIsRejected() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.redeemVoucher(request()));
        verify(voucherRepository, never()).save(any());
    }

    @Test
    void staffAccountCannotRedeem() {
        UserModel cashier = customer(500L);
        cashier.setRole(UserRole.CASHIER);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(cashier));

        assertThrows(RuntimeException.class, () -> service.redeemVoucher(request()));
        verify(userRepository, never()).deductPointsAtomic(eq(CUSTOMER_ID), anyLong());
    }

    /** The counter only sees types that are enabled and priced in points. */
    @Test
    void redeemableListSkipsInactiveAndPointlessCatalogs() {
        VoucherCatalogModel inactive = catalog(100);
        inactive.setId(22L);
        inactive.setStatus("inactive");
        VoucherCatalogModel notForPoints = catalog(0);
        notForPoints.setId(23L);
        when(voucherCatalogRepository.findAllByOrderByIdAsc())
                .thenReturn(java.util.List.of(catalog(100), inactive, notForPoints));

        var redeemable = service.getRedeemableVouchers();

        assertEquals(1, redeemable.size());
        assertEquals(CATALOG_ID, redeemable.get(0).getVoucherCatalogId());
        assertEquals(100, redeemable.get(0).getPointsRequired());
    }

    private RedeemVoucherRequest request() {
        RedeemVoucherRequest request = new RedeemVoucherRequest();
        request.setCustomerPhone(PHONE);
        request.setVoucherCatalogId(CATALOG_ID);
        return request;
    }

    private UserModel customer(long points) {
        UserModel customer = new UserModel();
        customer.setId(CUSTOMER_ID);
        customer.setFullName("Khách Hàng Một");
        customer.setPhone(PHONE);
        customer.setPoints(points);
        customer.setRole(UserRole.CUSTOMER);
        return customer;
    }

    private VoucherCatalogModel catalog(int pointsRequired) {
        VoucherCatalogModel catalog = new VoucherCatalogModel();
        catalog.setId(CATALOG_ID);
        catalog.setName("Giảm 10.000đ");
        catalog.setDiscountType("FIXED");
        catalog.setDiscountValue(new BigDecimal("10000"));
        catalog.setPointsRequired(pointsRequired);
        catalog.setStatus("active");
        return catalog;
    }
}
