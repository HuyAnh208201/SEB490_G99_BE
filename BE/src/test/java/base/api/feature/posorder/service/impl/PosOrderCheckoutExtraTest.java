package base.api.feature.posorder.service.impl;

import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.request.CheckoutLineRequest;
import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.promotion.dto.response.CampaignSummaryResponse;
import base.api.feature.promotion.service.ICampaignService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.OrderDiscountModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherCatalogModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra checkout paths (voucher, PAYOS, points) not covered by {@link PosOrderCheckoutTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosOrderCheckoutExtraTest {

    private static final Long BRANCH_ID = 10L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private OrderDiscountRepository orderDiscountRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private VoucherRepository voucherRepository;
    @Mock private VoucherCatalogRepository voucherCatalogRepository;
    @Mock private IProductRepository productRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IUserService userService;
    @Mock private ICashierService cashierService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private ICampaignService campaignService;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PosOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(BRANCH_ID);
        cashier.setRole(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);
        when(shiftRepository
                .findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
                        anyLong(), any(), any()))
                .thenReturn(List.of());
        when(orderRepository.save(any())).thenAnswer(call -> {
            var order = call.getArgument(0, base.api.shared.entity.OrderModel.class);
            if (order.getId() == null) {
                order.setId(99L);
            }
            return order;
        });
        when(cashierService.redeemValueOf(anyLong())).thenAnswer(call -> {
            long points = call.getArgument(0);
            if (points <= 0) {
                return BigDecimal.ZERO;
            }
            return BigDecimal.valueOf(points).multiply(BigDecimal.valueOf(1000));
        });
    }

    @Test
    void payOsCheckoutSetsPendingPaymentWithoutCash() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);

        CheckoutRequest request = baseRequest(1, 2);
        request.setPaymentMethod("PAYOS");
        request.setCashReceived(null);

        OrderResponse response = service.checkout(request);

        assertEquals("PENDING_PAYMENT", response.getStatus());
        assertEquals("PAYOS", response.getPaymentMethod());
        assertEquals("PENDING", response.getPaymentStatus());
        ArgumentCaptor<PaymentModel> payment = ArgumentCaptor.forClass(PaymentModel.class);
        verify(paymentRepository).save(payment.capture());
        assertEquals("PENDING", payment.getValue().getStatus());
        assertEquals("PAYOS", payment.getValue().getMethod());
    }

    @Test
    void fixedVoucherReducesTotalAndStoresDiscount() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        stubActiveVoucher("SAVE5K", "FIXED", "5000");
        when(voucherRepository.markUsed(11L)).thenReturn(1);

        CheckoutRequest request = cashRequest(1, 2, "30000");
        request.setVoucherCode("SAVE5K");

        OrderResponse response = service.checkout(request);

        assertEquals(0, new BigDecimal("19000").compareTo(response.getTotal()));
        assertEquals(0, new BigDecimal("5000").compareTo(response.getDiscountAmount()));
        verify(orderDiscountRepository).save(any());
        verify(voucherRepository).markUsed(11L);
    }

    @Test
    void percentVoucherRoundsHalfUp() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        stubActiveVoucher("TENPCT", "PERCENT", "10");
        when(voucherRepository.markUsed(11L)).thenReturn(1);

        OrderResponse response = service.checkout(cashWithVoucher(1, 2, "30000", "TENPCT"));

        // 10% of 24000 = 2400
        assertEquals(0, new BigDecimal("21600").compareTo(response.getTotal()));
        assertEquals(0, new BigDecimal("2400").compareTo(response.getDiscountAmount()));
    }

    @Test
    void fixedVoucherIsCappedAtSubtotal() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        stubActiveVoucher("HUGE", "FIXED", "999999");
        when(voucherRepository.markUsed(11L)).thenReturn(1);

        OrderResponse response = service.checkout(cashWithVoucher(1, 1, "0", "HUGE"));

        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotal()));
        assertEquals(0, new BigDecimal("12000").compareTo(response.getDiscountAmount()));
    }

    @Test
    void expiredVoucherIsRejected() {
        stubProduct(1, "Milk", "12000");
        VoucherModel voucher = voucherShell("OLD");
        voucher.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(voucherRepository.findByCodeIgnoreCase("OLD")).thenReturn(Optional.of(voucher));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(cashWithVoucher(1, 1, "20000", "OLD")));

        assertTrue(error.getMessage().contains("expired"));
        verify(branchInventoryRepository, never()).deductStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void usedVoucherIsRejected() {
        stubProduct(1, "Milk", "12000");
        VoucherModel voucher = voucherShell("USED");
        voucher.setStatus("used");
        when(voucherRepository.findByCodeIgnoreCase("USED")).thenReturn(Optional.of(voucher));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(cashWithVoucher(1, 1, "20000", "USED")));

        assertTrue(error.getMessage().contains("already been used"));
    }

    @Test
    void unknownVoucherIsNotFound() {
        stubProduct(1, "Milk", "12000");
        when(voucherRepository.findByCodeIgnoreCase("NOPE")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.checkout(cashWithVoucher(1, 1, "20000", "NOPE")));
    }

    @Test
    void voucherMarkUsedRaceFailsAfterStockDeduct() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        stubActiveVoucher("RACE", "FIXED", "1000");
        when(voucherRepository.markUsed(11L)).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(cashWithVoucher(1, 1, "20000", "RACE")));

        assertTrue(error.getMessage().contains("just used on another order"));
        verify(orderItemRepository, never()).saveAll(any());
    }

    @Test
    void pointsRedeemIsCappedByCustomerBalance() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        UserModel customer = customer(7L, 3L);
        when(userService.getOrCreateGuestByPhone(eq("0909111222"), any())).thenReturn(customer);
        when(cashierService.settlePoints(any(), any(), eq(3L)))
                .thenReturn(new ICashierService.PointSettlement(3L, 2L, 2L));

        CheckoutRequest request = cashRequest(1, 2, "30000");
        request.setCustomerPhone("0909111222");
        request.setPointsToRedeem(100L);

        OrderResponse response = service.checkout(request);

        // balance 3 * 1000 = 3000 off 24000
        assertEquals(0, new BigDecimal("21000").compareTo(response.getTotal()));
        assertEquals(3L, response.getPointsRedeemed());
        verify(cashierService).settlePoints(any(), any(), eq(3L));
    }

    @Test
    void pointsRedeemIsCappedByAmountLeft() {
        stubProduct(1, "Milk", "5000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        UserModel customer = customer(7L, 100L);
        when(userService.getOrCreateGuestByPhone(eq("0909111222"), any())).thenReturn(customer);
        when(cashierService.settlePoints(any(), any(), eq(5L)))
                .thenReturn(new ICashierService.PointSettlement(5L, 0L, 95L));

        CheckoutRequest request = cashRequest(1, 1, "0");
        request.setCustomerPhone("0909111222");
        request.setPointsToRedeem(50L);

        OrderResponse response = service.checkout(request);

        // 5000 / 1000 = 5 points max
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotal()));
        assertEquals(5L, response.getPointsRedeemed());
    }

    @Test
    void pointsAreIgnoredWithoutCustomerPhone() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);

        CheckoutRequest request = cashRequest(1, 1, "20000");
        request.setPointsToRedeem(10L);

        OrderResponse response = service.checkout(request);

        assertEquals(0L, response.getPointsRedeemed());
        verify(cashierService, never()).settlePoints(any(), any(), anyLong());
        verify(userService, never()).getOrCreateGuestByPhone(any(), any());
    }

    @Test
    void staffPhoneIsRejectedAsCustomer() {
        stubProduct(1, "Milk", "12000");
        UserModel staff = customer(3L, 0L);
        staff.setRole(UserRole.CASHIER);
        when(userService.getOrCreateGuestByPhone(eq("0911000000"), any())).thenReturn(staff);

        CheckoutRequest request = cashRequest(1, 1, "20000");
        request.setCustomerPhone("0911000000");

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkout(request));

        assertTrue(error.getMessage().contains("staff account"));
        verify(branchInventoryRepository, never()).deductStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void blankVoucherCodeIsIgnored() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);

        CheckoutRequest request = cashRequest(1, 1, "20000");
        request.setVoucherCode("   ");

        OrderResponse response = service.checkout(request);

        assertEquals(0, new BigDecimal("12000").compareTo(response.getTotal()));
        verify(voucherRepository, never()).findByCodeIgnoreCase(any());
    }

    @Test
    void voucherWithoutCatalogLinkIsRejected() {
        stubProduct(1, "Milk", "12000");
        VoucherModel voucher = voucherShell("ORPHAN");
        voucher.setVoucherCatalogId(null);
        when(voucherRepository.findByCodeIgnoreCase("ORPHAN")).thenReturn(Optional.of(voucher));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(cashWithVoucher(1, 1, "20000", "ORPHAN")));

        assertTrue(error.getMessage().contains("not linked to a discount type"));
    }

    @Test
    void voucherIssuedToAnotherCustomerIsRejected() {
        stubProduct(1, "Milk", "12000");
        VoucherModel voucher = voucherShell("MINE");
        voucher.setCustomerId(77L);
        when(voucherRepository.findByCodeIgnoreCase("MINE")).thenReturn(Optional.of(voucher));
        when(userService.getOrCreateGuestByPhone(eq("0909111222"), any())).thenReturn(customer(7L, 0L));

        CheckoutRequest request = cashWithVoucher(1, 1, "20000", "MINE");
        request.setCustomerPhone("0909111222");

        BusinessException error = assertThrows(BusinessException.class, () -> service.checkout(request));

        assertTrue(error.getMessage().contains("belongs to another customer"));
        verify(branchInventoryRepository, never()).deductStock(anyLong(), anyInt(), anyInt());
        verify(voucherRepository, never()).markUsed(anyLong());
    }

    @Test
    void customerOnlyVoucherIsRejectedForWalkIn() {
        stubProduct(1, "Milk", "12000");
        VoucherModel voucher = voucherShell("MINE");
        voucher.setCustomerId(77L);
        when(voucherRepository.findByCodeIgnoreCase("MINE")).thenReturn(Optional.of(voucher));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(cashWithVoucher(1, 1, "20000", "MINE")));

        // Chưa có khách trên đơn thì chưa biết mã của ai — báo việc cần làm, không
        // đổ cho cashier là dùng nhầm mã của người khác.
        assertTrue(error.getMessage().contains("issued to a specific customer"));
        verify(voucherRepository, never()).markUsed(anyLong());
    }

    @Test
    void voucherOfOwnerIsAccepted() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        stubActiveVoucher("MINE", "FIXED", "2000");
        VoucherModel voucher = voucherShell("MINE");
        voucher.setCustomerId(7L);
        when(voucherRepository.findByCodeIgnoreCase("MINE")).thenReturn(Optional.of(voucher));
        when(voucherRepository.markUsed(11L)).thenReturn(1);
        UserModel customer = customer(7L, 0L);
        when(userService.getOrCreateGuestByPhone(eq("0909111222"), any())).thenReturn(customer);
        when(cashierService.settlePoints(any(), any(), eq(0L)))
                .thenReturn(new ICashierService.PointSettlement(0L, 1L, 1L));

        CheckoutRequest request = cashWithVoucher(1, 1, "20000", "MINE");
        request.setCustomerPhone("0909111222");

        OrderResponse response = service.checkout(request);

        assertEquals(0, new BigDecimal("10000").compareTo(response.getTotal()));
        verify(voucherRepository).markUsed(11L);
    }

    @Test
    void voucherFromInactiveCatalogIsRejected() {
        stubProduct(1, "Milk", "12000");
        VoucherModel voucher = voucherShell("OFF");
        when(voucherRepository.findByCodeIgnoreCase("OFF")).thenReturn(Optional.of(voucher));
        VoucherCatalogModel catalog = new VoucherCatalogModel();
        catalog.setId(21L);
        catalog.setName("Promo");
        catalog.setDiscountType("FIXED");
        catalog.setDiscountValue(new BigDecimal("5000"));
        catalog.setStatus("inactive");
        when(voucherCatalogRepository.findById(21L)).thenReturn(Optional.of(catalog));

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> service.checkout(cashWithVoucher(1, 1, "20000", "OFF")));

        assertTrue(error.getMessage().contains("no longer available"));
        verify(voucherRepository, never()).markUsed(anyLong());
    }

    /**
     * Phản biện fix "catalog phải active": cột status là chuỗi tự do, dữ liệu cũ có thể
     * ghi hoa. Chặn nhầm 'ACTIVE' sẽ làm chết mọi mã đang chạy, nên khoá hành vi lại đây.
     */
    @Test
    void uppercaseActiveCatalogStatusIsStillAccepted() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        VoucherModel voucher = voucherShell("OK");
        when(voucherRepository.findByCodeIgnoreCase("OK")).thenReturn(Optional.of(voucher));
        VoucherCatalogModel catalog = new VoucherCatalogModel();
        catalog.setId(21L);
        catalog.setName("Promo");
        catalog.setDiscountType("FIXED");
        catalog.setDiscountValue(new BigDecimal("2000"));
        catalog.setStatus("ACTIVE");
        when(voucherCatalogRepository.findById(21L)).thenReturn(Optional.of(catalog));
        when(voucherRepository.markUsed(11L)).thenReturn(1);

        OrderResponse response = service.checkout(cashWithVoucher(1, 1, "20000", "OK"));

        assertEquals(0, new BigDecimal("10000").compareTo(response.getTotal()));
    }

    /** Mã dùng chung (customer_id null) vẫn phải áp được cho khách vãng lai. */
    @Test
    void sharedVoucherStillWorksForWalkIn() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        stubActiveVoucher("SHARED", "FIXED", "2000");
        when(voucherRepository.markUsed(11L)).thenReturn(1);

        OrderResponse response = service.checkout(cashWithVoucher(1, 1, "20000", "SHARED"));

        assertEquals(0, new BigDecimal("10000").compareTo(response.getTotal()));
        verify(voucherRepository).markUsed(11L);
    }

    /** Cashier gõ thừa khoảng trắng và sai hoa/thường vẫn phải ra đúng mã. */
    @Test
    void voucherCodeIsTrimmedBeforeLookup() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        stubActiveVoucher("save5k", "FIXED", "5000");
        when(voucherRepository.markUsed(11L)).thenReturn(1);

        service.checkout(cashWithVoucher(1, 1, "20000", "  save5k  "));

        verify(voucherRepository).findByCodeIgnoreCase("save5k");
    }

    /**
     * Thứ tự tính tiền: trừ voucher trước rồi mới quy đổi điểm. Đảo lại thì khách
     * bị đốt nhiều điểm hơn mức cần để phủ số tiền còn lại.
     */
    @Test
    void voucherIsAppliedBeforePointsAreRedeemed() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);
        stubActiveVoucher("SAVE5K", "FIXED", "5000");
        when(voucherRepository.markUsed(11L)).thenReturn(1);
        UserModel customer = customer(7L, 100L);
        when(userService.getOrCreateGuestByPhone(eq("0909111222"), any())).thenReturn(customer);
        when(cashierService.settlePoints(any(), any(), eq(7L)))
                .thenReturn(new ICashierService.PointSettlement(7L, 0L, 93L));

        CheckoutRequest request = cashWithVoucher(1, 1, "20000", "SAVE5K");
        request.setCustomerPhone("0909111222");
        request.setPointsToRedeem(100L);

        OrderResponse response = service.checkout(request);

        // 12000 - 5000 voucher = 7000 còn lại → chỉ đổi 7 điểm, không phải 12.
        assertEquals(7L, response.getPointsRedeemed());
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getTotal()));
        assertEquals(0, new BigDecimal("12000").compareTo(response.getDiscountAmount()));
    }

    // -------------------------------------------------------------------------
    // Khuyến mãi cửa hàng (campaign) — áp tự động, quầy không chọn được
    // -------------------------------------------------------------------------

    @Test
    void percentCampaignAppliesAutomaticallyAndWritesDiscountRowWithoutVoucher() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        stubApplicable(summary(5L, "Summer Sale", "PERCENT", "10"));

        // Request KHÔNG mang thông tin khuyến mãi nào — server tự áp.
        OrderResponse response = service.checkout(cashRequest(1, 2, "30000"));

        assertEquals(0, new BigDecimal("21600").compareTo(response.getTotal()));
        assertEquals(0, new BigDecimal("2400").compareTo(response.getDiscountAmount()));

        ArgumentCaptor<OrderDiscountModel> row = ArgumentCaptor.forClass(OrderDiscountModel.class);
        verify(orderDiscountRepository).save(row.capture());
        // voucher_id null là điều kiện để VoucherReleaseService bỏ qua dòng này lúc hoàn đơn.
        assertNull(row.getValue().getVoucherId());
        assertEquals("Summer Sale", row.getValue().getCode());
        assertEquals(0, new BigDecimal("2400").compareTo(row.getValue().getDiscountAmount()));
    }

    @Test
    void stackedCampaignsApplyOnTheRemainingAmountNotOnSubtotal() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        stubApplicable(
                summary(5L, "Ten percent", "PERCENT", "10"),
                summary(6L, "Five thousand off", "FIXED_AMOUNT", "5000"));

        OrderResponse response = service.checkout(cashRequest(1, 2, "30000"));

        // 24000 → −10% (2400) → 21600 → −5000 = 16600. Thứ tự do getApplicableForBranch
        // quyết định, quầy không đảo được.
        assertEquals(0, new BigDecimal("16600").compareTo(response.getTotal()));
        assertEquals(0, new BigDecimal("7400").compareTo(response.getDiscountAmount()));
        verify(orderDiscountRepository, times(2)).save(any());
    }

    /** Chi nhánh không có khuyến mãi nào thì đơn giữ nguyên giá, không lỗi. */
    @Test
    void checkoutWithoutAnyApplicableCampaignKeepsSubtotal() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        stubApplicable();

        OrderResponse response = service.checkout(cashRequest(1, 2, "30000"));

        assertEquals(0, new BigDecimal("24000").compareTo(response.getTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getDiscountAmount()));
        verify(orderDiscountRepository, never()).save(any());
    }

    /**
     * Khuyến mãi lấy theo chi nhánh của chính cashier. Quầy không gửi lên được chi
     * nhánh nào khác, và cũng không gửi được danh sách khuyến mãi nào.
     */
    @Test
    void campaignsAreLookedUpForTheCashierOwnBranch() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);
        stubApplicable(summary(5L, "Summer Sale", "PERCENT", "10"));

        service.checkout(cashRequest(1, 2, "30000"));

        verify(campaignService).getApplicableForBranch(BRANCH_ID);
    }

    private void stubApplicable(CampaignSummaryResponse... campaigns) {
        when(campaignService.getApplicableForBranch(BRANCH_ID)).thenReturn(List.of(campaigns));
    }

    private static CampaignSummaryResponse summary(Long id, String name, String type, String value) {
        CampaignSummaryResponse response = new CampaignSummaryResponse();
        response.setId(id);
        response.setName(name);
        response.setType(type);
        response.setDiscountValue(new BigDecimal(value));
        return response;
    }

    private void stubProduct(int id, String name, String price) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setName(name);
        product.setDefaultSalePrice(new BigDecimal(price));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
    }

    private void stubActiveVoucher(String code, String discountType, String value) {
        VoucherModel voucher = voucherShell(code);
        when(voucherRepository.findByCodeIgnoreCase(code)).thenReturn(Optional.of(voucher));
        VoucherCatalogModel catalog = new VoucherCatalogModel();
        catalog.setId(21L);
        catalog.setName("Promo");
        catalog.setDiscountType(discountType);
        catalog.setDiscountValue(new BigDecimal(value));
        when(voucherCatalogRepository.findById(21L)).thenReturn(Optional.of(catalog));
    }

    private VoucherModel voucherShell(String code) {
        VoucherModel voucher = new VoucherModel();
        voucher.setId(11L);
        voucher.setCode(code);
        voucher.setStatus("active");
        voucher.setVoucherCatalogId(21L);
        voucher.setExpiresAt(LocalDateTime.now().plusDays(7));
        return voucher;
    }

    private UserModel customer(Long id, Long points) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setFullName("Guest");
        user.setPhone("0909111222");
        user.setPoints(points);
        user.setRole(UserRole.CUSTOMER);
        return user;
    }

    private CheckoutRequest baseRequest(int productId, int qty) {
        CheckoutRequest request = new CheckoutRequest();
        request.getLines().add(line(productId, qty));
        return request;
    }

    private CheckoutRequest cashRequest(int productId, int qty, String cashReceived) {
        CheckoutRequest request = baseRequest(productId, qty);
        request.setPaymentMethod("CASH");
        request.setCashReceived(new BigDecimal(cashReceived));
        return request;
    }

    private CheckoutRequest cashWithVoucher(int productId, int qty, String cash, String code) {
        CheckoutRequest request = cashRequest(productId, qty, cash);
        request.setVoucherCode(code);
        return request;
    }

    private CheckoutLineRequest line(int productId, int qty) {
        CheckoutLineRequest line = new CheckoutLineRequest();
        line.setProductId(productId);
        line.setQuantity(qty);
        return line;
    }
}
