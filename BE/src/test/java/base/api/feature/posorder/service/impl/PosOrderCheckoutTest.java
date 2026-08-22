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
import base.api.feature.report.repository.PointTransactionRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.shift.repository.ShiftRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.OrderItemModel;
import base.api.shared.entity.OrderModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PosOrderCheckoutTest {

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
            OrderModel order = call.getArgument(0);
            if (order.getId() == null) order.setId(99L);
            return order;
        });
    }

    @Test
    void pricesComeFromTheDatabaseNotTheClient() {
        stubProduct(1, "Sữa tươi", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);

        OrderResponse response = service.checkout(cashRequest(1, 2, "100000"));

        // 2 x 12_000 = 24_000 regardless of what the client sent.
        assertEquals(0, new BigDecimal("24000").compareTo(response.getTotal()));
        ArgumentCaptor<List<OrderItemModel>> items = ArgumentCaptor.forClass(List.class);
        verify(orderItemRepository).saveAll(items.capture());
        assertEquals(0, new BigDecimal("12000").compareTo(items.getValue().get(0).getUnitPrice()));
    }

    @Test
    void outOfStockRejectsTheWholeOrder() {
        stubProduct(1, "Sữa tươi", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), anyInt())).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.checkout(cashRequest(1, 99, "5000000")));

        assertTrue(error.getMessage().contains("Not enough stock"));
        // Order must not be persisted and points must not be touched.
        verify(orderItemRepository, never()).saveAll(any());
        verify(paymentRepository, never()).save(any());
        verify(cashierService, never()).settlePoints(any(), any(), anyLong());
    }

    @Test
    void cashBelowTheAmountDueIsRejected() {
        stubProduct(1, "Sữa tươi", "12000");

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.checkout(cashRequest(1, 2, "1000")));

        assertTrue(error.getMessage().contains("Cash received"));
        // Stock must not be deducted because insufficient cash fails first.
        verify(branchInventoryRepository, never()).deductStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void duplicateLinesOfTheSameProductAreMergedBeforeTheStockCheck() {
        stubProduct(1, "Sữa tươi", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(5))).thenReturn(1);

        CheckoutRequest request = cashRequest(1, 2, "100000");
        request.getLines().add(line(1, 3));

        service.checkout(request);

        // Deduct once for quantity 5, not twice for 2 and 3 — separate checks would each pass while the total exceeds stock.
        verify(branchInventoryRepository).deductStock(BRANCH_ID, 1, 5);
    }

    @Test
    void productWithoutSalePriceIsRejected() {
        ProductModel product = new ProductModel();
        product.setId(1);
        product.setName("No Price");
        product.setDefaultSalePrice(null);
        when(productRepository.findAllById(any())).thenReturn(List.of(product));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.checkout(cashRequest(1, 1, "100000")));

        assertTrue(error.getMessage().contains("no sale price"));
        verify(branchInventoryRepository, never()).deductStock(anyLong(), anyInt(), anyInt());
    }

    @Test
    void missingProductIsRejected() {
        when(productRepository.findAllById(any())).thenReturn(List.of());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.checkout(cashRequest(99, 1, "100000")));

        assertTrue(error.getMessage().toLowerCase().contains("not found"));
    }

    @Test
    void exactCashReceivedIsAccepted() {
        stubProduct(1, "Sữa tươi", "12000");
        when(branchInventoryRepository.deductStock(eq(BRANCH_ID), eq(1), eq(2))).thenReturn(1);

        OrderResponse response = service.checkout(cashRequest(1, 2, "24000"));

        assertEquals(0, new BigDecimal("24000").compareTo(response.getTotal()));
        assertEquals("Nguyen Thu Ngan", response.getCashierName());
        assertEquals("ChainStore Quan 1", response.getBranchName());
        assertEquals("123 Nguyen Hue, Quan 1, TP.HCM", response.getBranchAddress());
        assertEquals("02811110001", response.getBranchPhone());
        verify(paymentRepository).save(any());
    }

    private void stubProduct(int id, String name, String price) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setName(name);
        product.setDefaultSalePrice(new BigDecimal(price));
        when(productRepository.findAllById(any())).thenReturn(List.of(product));
    }

    private CheckoutRequest cashRequest(int productId, int qty, String cashReceived) {
        CheckoutRequest request = new CheckoutRequest();
        request.getLines().add(line(productId, qty));
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
