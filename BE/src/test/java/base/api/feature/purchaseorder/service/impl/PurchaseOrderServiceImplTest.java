package base.api.feature.purchaseorder.service.impl;

import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaseorder.dto.request.CreatePurchaseOrderRequest;
import base.api.feature.purchaseorder.dto.response.PurchaseOrderResponse;
import base.api.feature.purchaseorder.mapper.PurchaseOrderMapper;
import base.api.feature.purchaseorder.repository.PurchaseOrderItemRepository;
import base.api.feature.purchaseorder.repository.PurchaseOrderRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.supplier.repository.ISupplierRepository;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseOrderItemModel;
import base.api.shared.entity.PurchaseOrderModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.SupplierModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.PurchaseOrderStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PurchaseOrderServiceImpl} create / receive / cancel paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseOrderServiceImplTest {

    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private PurchaseRequestRepository purchaseRequestRepository;
    @Mock private PurchaseRequestDetailRepository detailRepository;
    @Mock private WarehouseInventoryRepository warehouseInventoryRepository;
    @Mock private IProductRepository productRepository;
    @Mock private ISupplierRepository supplierRepository;
    @Mock private PurchaseOrderMapper purchaseOrderMapper;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private WarehouseStockAllocationHelper warehouseStockAllocationHelper;
    @Mock private ProductPackagingService productPackagingService;

    @InjectMocks
    private PurchaseOrderServiceImpl service;

    @Test
    void createOrderRejectsEmptyItems() {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierId(1);
        request.setItems(List.of());

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createOrder(request));

        assertEquals("At least one product must be added.", error.getMessage());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void createOrderRejectsMissingSupplier() {
        CreatePurchaseOrderRequest request = orderRequest(99, 10, 5);
        when(supplierRepository.findById(99)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.createOrder(request));

        assertEquals("Supplier not found.", error.getMessage());
    }

    @Test
    void createOrderRejectsNonPositiveQuantity() {
        CreatePurchaseOrderRequest request = orderRequest(1, 10, 0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createOrder(request));

        assertEquals("Quantity must be greater than zero.", error.getMessage());
    }

    @Test
    void createOrderRejectsMissingProduct() {
        CreatePurchaseOrderRequest request = orderRequest(1, 10, 5);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.createOrder(request));

        assertTrue(error.getMessage().contains("Product not found"));
    }

    @Test
    void createOrderFinalizesSupplierReceiptAndStocksWarehouseImmediately() {
        CreatePurchaseOrderRequest request = orderRequest(1, 10, 5);
        request.setSupplierDeliveryDate(LocalDate.of(2026, 8, 13));
        request.setDeliveredByName("Nguyen Van Giao");
        request.setDeliveredByPhone("0909123456");
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        ProductModel product = product(10);
        product.setSupplierId(1);
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product));
        when(productPackagingService.toBaseQty(eq(5), any(ProductModel.class))).thenReturn(120);
        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(8);
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK)).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        UserModel actor = new UserModel();
        actor.setId(7L);
        actor.setFullName("Warehouse Receiver");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> {
            PurchaseOrderModel saved = inv.getArgument(0);
            saved.setId(100L);
            return saved;
        });
        when(purchaseOrderItemRepository.findByPurchaseOrderId(100L)).thenReturn(List.of());
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-100");

        PurchaseOrderResponse response = service.createOrder(request);

        assertEquals(100L, response.getId());
        assertEquals(PurchaseOrderStatus.RECEIVED.name(), response.getStatus());
        assertEquals(128, inventory.getQuantity());
        assertEquals("Nguyen Van Giao", response.getDeliveredByName());
        assertEquals("Warehouse Receiver", response.getReceivedByName());
        ArgumentCaptor<PurchaseOrderModel> orderCaptor = ArgumentCaptor.forClass(PurchaseOrderModel.class);
        verify(purchaseOrderRepository).save(orderCaptor.capture());
        assertEquals(PurchaseOrderStatus.RECEIVED, orderCaptor.getValue().getStatus());
        assertEquals(7L, orderCaptor.getValue().getCreatedBy());
        assertEquals(7L, orderCaptor.getValue().getReceivedBy());
        assertNotNull(orderCaptor.getValue().getReceivedAt());
        verify(purchaseOrderItemRepository).saveAll(any());
        verify(warehouseInventoryRepository).saveAll(any());
    }

    @Test
    void createOrderAllowsProductsNotAssignedToSupplier() {
        CreatePurchaseOrderRequest request = orderRequest(1, 10, 5);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        ProductModel product = product(10);
        product.setSupplierId(2);
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product));
        when(productPackagingService.toBaseQty(eq(5), any(ProductModel.class))).thenReturn(120);
        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(8);
        when(warehouseInventoryRepository.findByProductIdIn(anyCollection())).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK)).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        UserModel actor = new UserModel();
        actor.setId(7L);
        actor.setFullName("Warehouse Receiver");
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> {
            PurchaseOrderModel saved = inv.getArgument(0);
            saved.setId(101L);
            return saved;
        });
        when(purchaseOrderItemRepository.findByPurchaseOrderId(101L)).thenReturn(List.of());
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-101");

        PurchaseOrderResponse response = service.createOrder(request);

        assertEquals(101L, response.getId());
        verify(purchaseOrderRepository).save(any());
    }

    @Test
    void searchProductsSearchesAllActiveProducts() {
        when(productRepository.searchActiveProducts(eq("%milk%"), any()))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.searchProducts(3, "milk");

        verify(productRepository).searchActiveProducts(eq("%milk%"), any());
        verify(productRepository, never()).searchActiveProductsBySupplier(any(), any(), any());
    }

    @Test
    void receiveOrderRejectsNonOrderedStatus() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.RECEIVED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.receiveOrder(50L));

        assertEquals("Only ordered purchase orders can be received.", error.getMessage());
    }

    @Test
    void receiveOrderThrowsWhenOrderMissing() {
        when(purchaseOrderRepository.findById(88L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.receiveOrder(88L));

        assertEquals("Purchase order not found.", error.getMessage());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    void receiveOrderRejectsCancelledStatus() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.CANCELLED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.receiveOrder(50L));

        assertEquals("Only ordered purchase orders can be received.", error.getMessage());
        verify(warehouseInventoryRepository, never()).save(any());
    }

    @Test
    void receiveOrderRejectsNullStatus() {
        PurchaseOrderModel order = purchaseOrder(50L, null);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.receiveOrder(50L));

        assertEquals("Only ordered purchase orders can be received.", error.getMessage());
    }

    @Test
    void receiveOrderRejectsAlreadyReceived() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.RECEIVED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.receiveOrder(50L));

        assertEquals("Only ordered purchase orders can be received.", error.getMessage());
        verify(purchaseOrderItemRepository, never()).findByPurchaseOrderId(any());
    }

    @Test
    void receiveOrderRejectsEmptyItems() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of());

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.receiveOrder(50L));

        assertEquals("Purchase order has no items to receive.", error.getMessage());
    }

    @Test
    void receiveOrderIncreasesWarehouseStockAndMarksReceived() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = new PurchaseOrderItemModel();
        item.setPurchaseOrderId(50L);
        item.setProductId(10);
        item.setQuantity(2);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        ProductModel product = product(10);
        when(productRepository.findByIdInWithCategory(Set.of(10))).thenReturn(List.of(product));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(10);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(any())).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        PurchaseOrderResponse response = service.receiveOrder(50L);

        assertEquals(PurchaseOrderStatus.RECEIVED.name(), response.getStatus());
        assertEquals(58, inventory.getQuantity());
        assertEquals(PurchaseOrderStatus.RECEIVED, order.getStatus());
        verify(warehouseInventoryRepository).saveAll(List.of(inventory));
    }

    @Test
    void receiveOrderCreatesWarehouseInventoryWhenMissing() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = poItem(50L, 10, 2);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        when(productRepository.findByIdInWithCategory(Set.of(10))).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of());
        when(warehouseInventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(any())).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        service.receiveOrder(50L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WarehouseInventoryModel>> inventoryCaptor = ArgumentCaptor.forClass(List.class);
        verify(warehouseInventoryRepository).saveAll(inventoryCaptor.capture());
        WarehouseInventoryModel saved = inventoryCaptor.getValue().get(0);
        assertEquals(10, saved.getProductId());
        assertEquals(48, saved.getQuantity());
        assertEquals(0, saved.getReorderPoint());
    }

    @Test
    void receiveOrderConvertsTopUnitsToBaseViaPackaging() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = poItem(50L, 10, 3);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        ProductModel product = product(10);
        when(productRepository.findByIdInWithCategory(Set.of(10))).thenReturn(List.of(product));
        when(productPackagingService.toBaseQty(eq(3), any(ProductModel.class))).thenReturn(72);

        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(5);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(any())).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        service.receiveOrder(50L);

        verify(productPackagingService).toBaseQty(eq(3), any(ProductModel.class));
        assertEquals(77, inventory.getQuantity());
    }

    @Test
    void receiveOrderSetsReceivedAt() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = poItem(50L, 10, 1);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        when(productRepository.findByIdInWithCategory(Set.of(10))).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(24);

        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(0);
        when(warehouseInventoryRepository.findByProductId(10)).thenReturn(Optional.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(any())).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        PurchaseOrderResponse response = service.receiveOrder(50L);

        assertNotNull(order.getReceivedAt());
        assertNotNull(response.getReceivedAt());
        assertEquals(PurchaseOrderStatus.RECEIVED, order.getStatus());
    }

    @Test
    void receiveOrderCallsReconcileApprovedStockStatus() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = poItem(50L, 10, 1);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        when(productRepository.findByIdInWithCategory(Set.of(10))).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(24);

        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(0);
        when(warehouseInventoryRepository.findByProductId(10)).thenReturn(Optional.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK)).thenReturn(List.of());
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        service.receiveOrder(50L);

        verify(warehouseStockAllocationHelper).reconcileApprovedStockStatus();
    }

    @Test
    void receiveOrderPromotesAwaitingStockWhenEnough() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = poItem(50L, 10, 2);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(0);
        when(warehouseInventoryRepository.findByProductId(10)).thenReturn(Optional.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseRequestModel awaiting = new PurchaseRequestModel();
        awaiting.setId(30L);
        awaiting.setStatus(PurchaseRequestStatus.AWAITING_STOCK);
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK))
                .thenReturn(new ArrayList<>(List.of(awaiting)));

        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setPurchaseRequestId(30L);
        detail.setProductId(10);
        detail.setApprovedQuantity(2);
        when(detailRepository.findByPurchaseRequestIdIn(List.of(30L))).thenReturn(List.of(detail));

        Map<Integer, Integer> workingStock = new HashMap<>();
        workingStock.put(10, 48);
        when(warehouseStockAllocationHelper.workingStockAfterApprovedReservations()).thenReturn(workingStock);
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        service.receiveOrder(50L);

        assertEquals(PurchaseRequestStatus.APPROVED, awaiting.getStatus());
        verify(purchaseRequestRepository).saveAll(List.of(awaiting));
        verify(warehouseStockAllocationHelper).reconcileApprovedStockStatus();
    }

    @Test
    void receiveOrderLeavesAwaitingWhenStillShort() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        PurchaseOrderItemModel item = poItem(50L, 10, 1);
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of(item));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(24);
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(10);
        inventory.setQuantity(0);
        when(warehouseInventoryRepository.findByProductId(10)).thenReturn(Optional.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseRequestModel awaiting = new PurchaseRequestModel();
        awaiting.setId(30L);
        awaiting.setStatus(PurchaseRequestStatus.AWAITING_STOCK);
        when(purchaseRequestRepository.findByStatus(PurchaseRequestStatus.AWAITING_STOCK))
                .thenReturn(new ArrayList<>(List.of(awaiting)));

        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setPurchaseRequestId(30L);
        detail.setProductId(10);
        detail.setApprovedQuantity(2);
        when(detailRepository.findByPurchaseRequestIdIn(List.of(30L))).thenReturn(List.of(detail));

        Map<Integer, Integer> workingStock = new HashMap<>();
        workingStock.put(10, 24);
        when(warehouseStockAllocationHelper.workingStockAfterApprovedReservations()).thenReturn(workingStock);
        when(warehouseStockAllocationHelper.reconcileApprovedStockStatus()).thenReturn(0);
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        service.receiveOrder(50L);

        assertEquals(PurchaseRequestStatus.AWAITING_STOCK, awaiting.getStatus());
        verify(purchaseRequestRepository, never()).saveAll(any());
        verify(warehouseStockAllocationHelper).reconcileApprovedStockStatus();
    }

    @Test
    void cancelOrderRejectsNonOrderedStatus() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.CANCELLED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.cancelOrder(50L));

        assertEquals("Only ordered purchase orders can be cancelled.", error.getMessage());
    }

    @Test
    void cancelOrderMarksOrderedAsCancelled() {
        PurchaseOrderModel order = purchaseOrder(50L, PurchaseOrderStatus.ORDERED);
        when(purchaseOrderRepository.findById(50L)).thenReturn(Optional.of(order));
        when(purchaseOrderRepository.save(any(PurchaseOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(purchaseOrderItemRepository.findByPurchaseOrderId(50L)).thenReturn(List.of());
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));
        when(purchaseOrderMapper.toOrderNumber(any())).thenReturn("PO-50");

        PurchaseOrderResponse response = service.cancelOrder(50L);

        assertEquals(PurchaseOrderStatus.CANCELLED.name(), response.getStatus());
        assertEquals(PurchaseOrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void getOrderThrowsWhenMissing() {
        when(purchaseOrderRepository.findById(88L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.getOrder(88L));

        assertEquals("Purchase order not found.", error.getMessage());
    }

    @Test
    void createOrderRejectsNullProductOnItem() {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierId(1);
        CreatePurchaseOrderRequest.Item item = new CreatePurchaseOrderRequest.Item();
        item.setProductId(null);
        item.setQuantity(1);
        request.setItems(List.of(item));
        when(supplierRepository.findById(1)).thenReturn(Optional.of(supplier(1)));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createOrder(request));

        assertEquals("Product is required.", error.getMessage());
    }

    private static CreatePurchaseOrderRequest orderRequest(Integer supplierId, Integer productId, Integer qty) {
        CreatePurchaseOrderRequest request = new CreatePurchaseOrderRequest();
        request.setSupplierId(supplierId);
        CreatePurchaseOrderRequest.Item item = new CreatePurchaseOrderRequest.Item();
        item.setProductId(productId);
        item.setQuantity(qty);
        item.setUnitPrice(new BigDecimal("10000"));
        request.setItems(List.of(item));
        return request;
    }

    private static PurchaseOrderItemModel poItem(Long orderId, Integer productId, Integer qty) {
        PurchaseOrderItemModel item = new PurchaseOrderItemModel();
        item.setPurchaseOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(qty);
        return item;
    }

    private static SupplierModel supplier(Integer id) {
        SupplierModel supplier = new SupplierModel();
        supplier.setId(id);
        supplier.setName("Supplier " + id);
        return supplier;
    }

    private static ProductModel product(Integer id) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setCode("P" + id);
        product.setName("Product " + id);
        product.setUnit("bottle");
        product.setReferenceImportPrice(new BigDecimal("8000"));
        return product;
    }

    private static PurchaseOrderModel purchaseOrder(Long id, PurchaseOrderStatus status) {
        PurchaseOrderModel order = new PurchaseOrderModel();
        order.setId(id);
        order.setSupplierId(1);
        order.setStatus(status);
        return order;
    }
}
