package base.api.feature.posorder.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.auth.service.IUserService;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.cashier.service.ICashierService;
import base.api.feature.posorder.dto.response.OrderResponse;
import base.api.feature.posorder.repository.OrderItemRepository;
import base.api.feature.posorder.repository.OrderRepository;
import base.api.feature.posorder.repository.PaymentRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.ShiftModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.ShiftStatus;
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
 * Unit tests for {@link PosOrderServiceImpl#getOrderById}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosOrderLookupTest {

    private static final Long BRANCH_ID = 10L;
    private static final Long ORDER_ID = 1L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private IProductRepository productRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private ShiftRepository shiftRepository;
    @Mock private IUserService userService;
    @Mock private IUserRepository userRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private ICashierService cashierService;
    @Mock private PointTransactionRepository pointTransactionRepository;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PosOrderServiceImpl service;

    @BeforeEach
    void setUp() {
        asCashier();
        ShiftModel shift = new ShiftModel();
        shift.setId(8L);
        shift.setStatus(ShiftStatus.PUBLISHED);
        when(shiftRepository.findByBranchIdAndStartTimeLessThanAndEndTimeGreaterThanOrderByStartTimeAsc(
                any(), any(), any())).thenReturn(List.of(shift));
        when(orderItemRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(paymentRepository.findByOrderIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());
        when(branchRepository.findAllById(any())).thenReturn(List.of());
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
        order.setCashierId(3L);
        order.setCustomerId(7L);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setFullName("Nguyen Thu Ngan");
        UserModel customer = new UserModel();
        customer.setId(7L);
        customer.setFullName("Khach Hang Mot");
        customer.setPhone("0911111111");
        when(userRepository.findAllById(any())).thenReturn(List.of(cashier, customer));
        BranchModel branch = new BranchModel();
        branch.setId(BRANCH_ID);
        branch.setName("ChainStore Quan 1");
        branch.setAddress("123 Nguyen Hue, Quan 1, TP.HCM");
        branch.setPhone("02811110001");
        when(branchRepository.findAllById(any())).thenReturn(List.of(branch));

        OrderResponse response = service.getOrderById(ORDER_ID);

        assertEquals(ORDER_ID, response.getId());
        assertEquals("INV-001", response.getInvoiceCode());
        assertEquals(BRANCH_ID, response.getBranchId());
        assertEquals("Nguyen Thu Ngan", response.getCashierName());
        assertEquals("ChainStore Quan 1", response.getBranchName());
        assertEquals("123 Nguyen Hue, Quan 1, TP.HCM", response.getBranchAddress());
        assertEquals("02811110001", response.getBranchPhone());
        assertEquals("Khach Hang Mot", response.getCustomerName());
        assertEquals("0911111111", response.getCustomerPhone());
        verify(orderItemRepository).findByOrderIdIn(List.of(ORDER_ID));
    }

    @Test
    void getOrderByIdRejectsOrderOutsideCurrentShift() {
        OrderModel order = order(ORDER_ID, BRANCH_ID);
        order.setShiftId(9L);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.getOrderById(ORDER_ID));

        assertTrue(error.getMessage().contains("current shift"));
    }

    private void asCashier() {
        UserModel cashier = new UserModel();
        cashier.setId(3L);
        cashier.setBranchId(BRANCH_ID);
        cashier.setFullName("Nguyen Thu Ngan");
        cashier.setRole(UserRole.CASHIER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(cashier);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.CASHIER);
    }

    private static OrderModel order(Long id, Long branchId) {
        OrderModel order = new OrderModel();
        order.setId(id);
        order.setBranchId(branchId);
        order.setShiftId(8L);
        order.setStatus("COMPLETED");
        order.setSubtotal(new BigDecimal("24000"));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotal(new BigDecimal("24000"));
        order.setCreatedAt(LocalDateTime.now());
        return order;
    }
}
