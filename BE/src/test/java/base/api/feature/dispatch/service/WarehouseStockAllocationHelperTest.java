package base.api.feature.dispatch.service;

import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.PurchaseRequestStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for public warehouse stock allocation helpers with TOP→BASE conversion.
 */
@ExtendWith(MockitoExtension.class)
class WarehouseStockAllocationHelperTest {

    private static final Integer PRODUCT_ID = 7;

    @Mock private WarehouseInventoryRepository warehouseInventoryRepository;
    @Mock private PurchaseRequestRepository purchaseRequestRepository;
    @Mock private PurchaseRequestDetailRepository detailRepository;
    @Mock private IProductRepository productRepository;
    @Mock private ProductPackagingService productPackagingService;

    @InjectMocks
    private WarehouseStockAllocationHelper helper;

    @Test
    void loadPhysicalStockMapsQuantitiesAndTreatsNullAsZero() {
        WarehouseInventoryModel row = stock(PRODUCT_ID, 40);
        WarehouseInventoryModel nullQty = stock(8, null);
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of(row, nullQty));

        Map<Integer, Integer> stock = helper.loadPhysicalStock();

        assertEquals(40, stock.get(PRODUCT_ID));
        assertEquals(0, stock.get(8));
    }

    @Test
    void canApproveRequestWhenWorkingStockCoversNeed() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 200)));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(5), any(ProductModel.class))).thenReturn(120);

        PurchaseRequestDetailModel detail = detail(1L, PRODUCT_ID, 5, 5);
        assertTrue(helper.canApproveRequest(99L, List.of(detail)));
    }

    @Test
    void canApproveRequestFailsWhenStockInsufficient() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 50)));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(5), any(ProductModel.class))).thenReturn(120);

        assertFalse(helper.canApproveRequest(99L, List.of(detail(1L, PRODUCT_ID, 5, 5))));
    }

    @Test
    void canApproveRequestSkipsZeroNeedAndNullProduct() {
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());

        PurchaseRequestDetailModel zero = detail(1L, PRODUCT_ID, 0, 0);
        PurchaseRequestDetailModel noProduct = detail(2L, null, 5, 5);

        assertTrue(helper.canApproveRequest(99L, List.of(zero, noProduct)));
    }

    @Test
    void canApproveUsesRequestedQtyWhenApprovedIsNull() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        PurchaseRequestDetailModel detail = detail(1L, PRODUCT_ID, 2, null);
        assertTrue(helper.canApproveRequest(99L, List.of(detail)));
    }

    @Test
    void workingStockAfterApprovedReservationsDeductsReservedNeed() {
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of(stock(PRODUCT_ID, 200)));
        PurchaseRequestModel approved = request(10L, LocalDateTime.now().minusHours(1));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(approved));
        when(detailRepository.findByPurchaseRequestIdIn(List.of(10L)))
                .thenReturn(List.of(detail(10L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(24);

        Map<Integer, Integer> working = helper.workingStockAfterApprovedReservations();

        assertEquals(176, working.get(PRODUCT_ID));
    }

    @Test
    void workingStockAfterApprovedReservationsIncludesUnreservedProducts() {
        Integer awaitingProductId = 8;
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of(
                stock(PRODUCT_ID, 200),
                stock(awaitingProductId, 38)));
        PurchaseRequestModel approved = request(10L, LocalDateTime.now().minusHours(1));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(approved));
        when(detailRepository.findByPurchaseRequestIdIn(List.of(10L)))
                .thenReturn(List.of(detail(10L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(24);

        Map<Integer, Integer> working = helper.workingStockAfterApprovedReservations();

        assertEquals(176, working.get(PRODUCT_ID));
        assertEquals(38, working.get(awaitingProductId));
    }

    @Test
    void workingStockAfterApprovedReservationsReturnsPhysicalWhenNoneApproved() {
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of(stock(PRODUCT_ID, 33)));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());

        Map<Integer, Integer> working = helper.workingStockAfterApprovedReservations();

        assertEquals(33, working.get(PRODUCT_ID));
        verify(detailRepository, never()).findByPurchaseRequestIdIn(any());
    }

    @Test
    void filterDispatchableApprovedReturnsEmptyForEmptyInput() {
        assertTrue(helper.filterDispatchableApproved(List.of()).isEmpty());
    }

    @Test
    void filterDispatchableApprovedUsesFifoAndSkipsInsufficient() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        PurchaseRequestModel older = request(1L, LocalDateTime.now().minusDays(2));
        PurchaseRequestModel newer = request(2L, LocalDateTime.now().minusDays(1));
        when(detailRepository.findByPurchaseRequestIdIn(any()))
                .thenReturn(List.of(
                        detail(1L, PRODUCT_ID, 1, 1),
                        detail(2L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        List<PurchaseRequestModel> result = helper.filterDispatchableApproved(List.of(newer, older));

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    @Test
    void reconcileApprovedStockStatusReturnsZeroWhenNoneApproved() {
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());

        assertEquals(0, helper.reconcileApprovedStockStatus());
        verify(purchaseRequestRepository, never()).saveAll(any());
    }

    @Test
    void reconcileApprovedStockStatusDemotesUndispatchable() {
        PurchaseRequestModel keep = request(1L, LocalDateTime.now().minusDays(2));
        PurchaseRequestModel demote = request(2L, LocalDateTime.now().minusDays(1));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(keep, demote));
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        when(detailRepository.findByPurchaseRequestIdIn(any()))
                .thenReturn(List.of(
                        detail(1L, PRODUCT_ID, 1, 1),
                        detail(2L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        int demoted = helper.reconcileApprovedStockStatus();

        assertEquals(1, demoted);
        assertEquals(PurchaseRequestStatus.AWAITING_STOCK, demote.getStatus());
        assertEquals(PurchaseRequestStatus.APPROVED, keep.getStatus());
        verify(purchaseRequestRepository).saveAll(List.of(demote));
    }

    @Test
    void canApproveExcludesSameRequestFromApprovedReservations() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        PurchaseRequestModel self = request(99L, LocalDateTime.now());
        self.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(self));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        // Self is excluded from reservations, so 48 base units remain available.
        assertTrue(helper.canApproveRequest(99L, List.of(detail(99L, PRODUCT_ID, 1, 1))));
        verify(detailRepository, never()).findByPurchaseRequestIdIn(any());
    }

    @Test
    void canApproveRequestTrueWhenExactStockEqualsNeed() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        assertTrue(helper.canApproveRequest(99L, List.of(detail(99L, PRODUCT_ID, 2, 2))));
    }

    @Test
    void canApproveRequestFalseWhenOtherApprovedReservesStock() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        PurchaseRequestModel other = request(10L, LocalDateTime.now().minusHours(1));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(other));
        when(detailRepository.findByPurchaseRequestIdIn(List.of(10L)))
                .thenReturn(List.of(detail(10L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        assertFalse(helper.canApproveRequest(99L, List.of(detail(99L, PRODUCT_ID, 1, 1))));
    }

    @Test
    void canApproveRequestTrueForEmptyDetails() {
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of());
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());

        assertTrue(helper.canApproveRequest(99L, List.of()));
    }

    @Test
    void canApproveRequestFalseWhenOneOfMultiProductShort() {
        Integer otherProduct = 8;
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(
                stock(PRODUCT_ID, 100),
                stock(otherProduct, 10)));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED)).thenReturn(List.of());
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID), product(otherProduct)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenAnswer(inv -> {
            ProductModel p = inv.getArgument(1);
            return PRODUCT_ID.equals(p.getId()) ? 24 : 48;
        });

        assertFalse(helper.canApproveRequest(99L, List.of(
                detail(99L, PRODUCT_ID, 1, 1),
                detail(99L, otherProduct, 1, 1))));
    }

    @Test
    void workingStockLeavesMissingProductAsZeroThenNegative() {
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of(stock(PRODUCT_ID, 10)));
        PurchaseRequestModel approved = request(10L, LocalDateTime.now());
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(approved));
        Integer missingProduct = 99;
        when(detailRepository.findByPurchaseRequestIdIn(List.of(10L)))
                .thenReturn(List.of(detail(10L, missingProduct, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(missingProduct)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(5);

        Map<Integer, Integer> working = helper.workingStockAfterApprovedReservations();

        assertEquals(10, working.get(PRODUCT_ID));
        assertEquals(-5, working.get(missingProduct));
    }

    @Test
    void filterDispatchableApprovedOrdersByCreatedAtNullsLast() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 96)));
        PurchaseRequestModel withDate = request(1L, LocalDateTime.now().minusDays(1));
        PurchaseRequestModel nullDate = request(2L, null);
        when(detailRepository.findByPurchaseRequestIdIn(any()))
                .thenReturn(List.of(
                        detail(1L, PRODUCT_ID, 1, 1),
                        detail(2L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        List<PurchaseRequestModel> result = helper.filterDispatchableApproved(List.of(nullDate, withDate));

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }

    @Test
    void filterDispatchableApprovedReturnsBothWhenBothFit() {
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 96)));
        PurchaseRequestModel older = request(1L, LocalDateTime.now().minusDays(2));
        PurchaseRequestModel newer = request(2L, LocalDateTime.now().minusDays(1));
        when(detailRepository.findByPurchaseRequestIdIn(any()))
                .thenReturn(List.of(
                        detail(1L, PRODUCT_ID, 1, 1),
                        detail(2L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        List<PurchaseRequestModel> result = helper.filterDispatchableApproved(List.of(newer, older));

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(2L, result.get(1).getId());
    }

    @Test
    void reconcileApprovedStockStatusNoOpWhenAllDispatchable() {
        PurchaseRequestModel keep = request(1L, LocalDateTime.now().minusDays(1));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(keep));
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 48)));
        when(detailRepository.findByPurchaseRequestIdIn(any()))
                .thenReturn(List.of(detail(1L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        int demoted = helper.reconcileApprovedStockStatus();

        assertEquals(0, demoted);
        assertEquals(PurchaseRequestStatus.APPROVED, keep.getStatus());
        verify(purchaseRequestRepository, never()).saveAll(any());
    }

    @Test
    void reconcileApprovedStockStatusDemotesAllWhenAllShort() {
        PurchaseRequestModel first = request(1L, LocalDateTime.now().minusDays(2));
        PurchaseRequestModel second = request(2L, LocalDateTime.now().minusDays(1));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.APPROVED))
                .thenReturn(List.of(first, second));
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(stock(PRODUCT_ID, 10)));
        when(detailRepository.findByPurchaseRequestIdIn(any()))
                .thenReturn(List.of(
                        detail(1L, PRODUCT_ID, 1, 1),
                        detail(2L, PRODUCT_ID, 1, 1)));
        when(productRepository.findByIdInWithCategory(anyCollection()))
                .thenReturn(List.of(product(PRODUCT_ID)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(48);

        int demoted = helper.reconcileApprovedStockStatus();

        assertEquals(2, demoted);
        assertEquals(PurchaseRequestStatus.AWAITING_STOCK, first.getStatus());
        assertEquals(PurchaseRequestStatus.AWAITING_STOCK, second.getStatus());
        verify(purchaseRequestRepository).saveAll(List.of(first, second));
    }

    private static WarehouseInventoryModel stock(Integer productId, Integer qty) {
        WarehouseInventoryModel row = new WarehouseInventoryModel();
        row.setProductId(productId);
        row.setQuantity(qty);
        return row;
    }

    private static ProductModel product(Integer id) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setName("SKU-" + id);
        return product;
    }

    private static PurchaseRequestModel request(Long id, LocalDateTime createdAt) {
        PurchaseRequestModel request = new PurchaseRequestModel();
        request.setId(id);
        request.setStatus(PurchaseRequestStatus.APPROVED);
        request.setCreatedAt(createdAt);
        return request;
    }

    private static PurchaseRequestDetailModel detail(
            Long requestId, Integer productId, Integer requested, Integer approved) {
        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setPurchaseRequestId(requestId);
        detail.setProductId(productId);
        detail.setRequestedQty(requested);
        detail.setApprovedQuantity(approved);
        return detail;
    }
}
