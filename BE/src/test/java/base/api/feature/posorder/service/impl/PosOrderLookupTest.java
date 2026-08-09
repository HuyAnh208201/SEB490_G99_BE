package base.api.feature.posorder.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.repository.OrderDiscountRepository;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.posorder.repository.VoucherCatalogRepository;
import base.api.feature.posorder.repository.VoucherRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.VoucherModel;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BusinessException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PosOrderServiceImpl#getOrderById} and {@link PosOrderServiceImpl#lookupVoucher}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosOrderLookupTest {

    private static final Long BRANCH_ID = 10L;
    private static final Long ORDER_ID = 1L;

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
    @Mock private IUserRepository userRepository;
    @Mock private ICashierService cashierService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PosOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        asCashier();
        when(orderItemRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(paymentRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
    }

    // -------------------------------------------------------------------------
    // getOrderById
    // -------------------------------------------------------------------------

    @Test
    void getOrderByIdRejectsUnknownOrder() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.getOrderById(ORDER_ID));

        assertTrue(error.getMessage().contains("Order not found."));
    }

    @Test
    void getOrderByIdRejectsOrderFromAnotherBranch() {
        OrderModel order = order(ORDER_ID, 99L);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.getOrderById(ORDER_ID));

        assertTrue(error.getMessage().contains("This order belongs to another branch."));
    }

    @Test
    void getOrderByIdReturnsHydratedOrderForSameBranch() {
        OrderModel order = order(ORDER_ID, BRANCH_ID);
        order.setInvoiceCode("INV-001");
        order.setTotal(new BigDecimal("24000"));
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderResponse response = service.getOrderById(ORDER_ID);

        assertEquals(ORDER_ID, response.getId());
        assertEquals("INV-001", response.getInvoiceCode());
        assertEquals(BRANCH_ID, response.getBranchId());
        verify(orderItemRepository).findByOrderIdIn(List.of(ORDER_ID));
    }

    // -------------------------------------------------------------------------
    // lookupVoucher (via resolveVoucher)
    // -------------------------------------------------------------------------

    @Test
    void lookupVoucherRejectsUnknownCode() {
        when(voucherRepository.findByCodeIgnoreCase("MISSING")).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.lookupVoucher("MISSING"));

        assertTrue(error.getMessage().contains("Discount code not found."));
    }

    @Test
    void lookupVoucherRejectsBlankCode() {
        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.lookupVoucher("   "));

        assertTrue(error.getMessage().contains("Discount code not found."));
    }

    @Test
    void lookupVoucherRejectsUsedCode() {
        VoucherModel voucher = voucher("USED10", "used", LocalDateTime.now().plusDays(1));
        when(voucherRepository.findByCodeIgnoreCase("USED10")).thenReturn(Optional.of(voucher));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.lookupVoucher("USED10"));

        assertTrue(error.getMessage().contains("This discount code has already been used."));
    }

    @Test
    void lookupVoucherRejectsExpiredCode() {
        VoucherModel voucher = voucher("OLD10", "active", LocalDateTime.now().minusDays(1));
        when(voucherRepository.findByCodeIgnoreCase("OLD10")).thenReturn(Optional.of(voucher));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.lookupVoucher("OLD10"));

        assertTrue(error.getMessage().contains("This discount code has expired."));
    }

    private void asCashier() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(BRANCH_ID);
        cashier.setRole(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);
    }

    private static OrderModel order(Long id, Long branchId) {
        OrderModel order = new OrderModel();
        order.setId(id);
        order.setBranchId(branchId);
        order.setStatus("COMPLETED");
        order.setSubtotal(new BigDecimal("24000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("24000"));
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }

    private static VoucherModel voucher(String code, String status, LocalDateTime expiresAt) {
        VoucherModel voucher = new VoucherModel();
        voucher.setId(5L);
        voucher.setCode(code);
        voucher.setStatus(status);
        voucher.setExpiresAt(expiresAt);
        voucher.setVoucherCatalogId(1L);
        return voucher;
    }
}
