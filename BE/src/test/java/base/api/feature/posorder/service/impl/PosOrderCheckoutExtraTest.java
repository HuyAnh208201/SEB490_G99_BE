package base.api.feature.posorder.service.impl;

import base.api.feature.auth.service.IUserService;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.request.CheckoutLineRequest;
import base.api.feature.posorder.dto.request.CheckoutRequest;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductCostService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.PaymentModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra checkout paths (PAYOS, points) not covered by {@link PosOrderCheckoutTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosOrderCheckoutExtraTest {

    private static final Long BRANCH_ID = 10L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private IProductRepository productRepository;
    @Mock private ProductCostService productCostService;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IUserService userService;
    @Mock private IBranchRepository branchRepository;
    @Mock private ICashierService cashierService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PosOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(BRANCH_ID);
        cashier.setFullName("Nguyen Thu Ngan");
        cashier.setRole(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);
        BranchModel branch = new BranchModel();
        branch.setId(BRANCH_ID);
        branch.setName("ChainStore Quan 1");
        branch.setAddress("123 Nguyen Hue, Quan 1, TP.HCM");
        branch.setPhone("02811110001");
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(productCostService.unitCostForProduct(any())).thenReturn(BigDecimal.ZERO);
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
    void legacyVoucherCodeFieldIsIgnoredOnCheckout() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);

        CheckoutRequest request = cashRequest(1, 2, "30000");
        request.setVoucherCode("SAVE5K");

        OrderResponse response = service.checkout(request);

        assertEquals(0, new BigDecimal("24000").compareTo(response.getTotal()));
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getDiscountAmount()));
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
        assertEquals("Guest", response.getCustomerName());
        assertEquals("0909111222", response.getCustomerPhone());
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
    void blankLegacyVoucherCodeFieldIsIgnored() {
        stubProduct(1, "Milk", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(1))).thenReturn(1);

        CheckoutRequest request = cashRequest(1, 1, "20000");
        request.setVoucherCode("   ");

        OrderResponse response = service.checkout(request);

        assertEquals(0, new BigDecimal("12000").compareTo(response.getTotal()));
    }

    private void stubProduct(int id, String name, String price) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setName(name);
        product.setDefaultSalePrice(new BigDecimal(price));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
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

    private CheckoutLineRequest line(int productId, int qty) {
        CheckoutLineRequest line = new CheckoutLineRequest();
        line.setProductId(productId);
        line.setQuantity(qty);
        return line;
    }
}
