package base.api.feature.dispatch.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.dispatch.dto.request.CreateDispatchOrderRequest;
import base.api.feature.dispatch.dto.request.UpdateDispatchStatusRequest;
import base.api.feature.dispatch.dto.response.DispatchOrderResponse;
import base.api.feature.dispatch.mapper.DispatchMapper;
import base.api.feature.dispatch.repository.DispatchOrderRepository;
import base.api.feature.dispatch.repository.DispatchOrderRequestRepository;
import base.api.feature.dispatch.repository.DispatchOrderSupplierRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.supplier.repository.ISupplierRepository;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.DispatchOrderModel;
import base.api.shared.entity.DispatchOrderRequestModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.DispatchStatus;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.exception.BadRequestException;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DispatchServiceImpl} create / status-update paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchServiceImplTest {

    @Mock private DispatchOrderRepository dispatchOrderRepository;
    @Mock private DispatchOrderRequestRepository dispatchOrderRequestRepository;
    @Mock private DispatchOrderSupplierRepository dispatchOrderSupplierRepository;
    @Mock private PurchaseRequestRepository purchaseRequestRepository;
    @Mock private PurchaseRequestDetailRepository detailRepository;
    @Mock private WarehouseInventoryRepository warehouseInventoryRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private IProductRepository productRepository;
    @Mock private ISupplierRepository supplierRepository;
    @Mock private DispatchMapper dispatchMapper;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private ProductPackagingService productPackagingService;
    @Mock private IUserRepository userRepository;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;

    @InjectMocks
    private DispatchServiceImpl service;

    @BeforeEach
    void stubSupplierLookups() {
        when(dispatchOrderSupplierRepository.findByDispatchOrderId(any())).thenReturn(List.of());
        when(dispatchOrderSupplierRepository.findByDispatchOrderIdIn(anyCollection())).thenReturn(List.of());
        when(dispatchOrderSupplierRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void createDispatchOrderRejectsNullRequestId() {
        CreateDispatchOrderRequest request = new CreateDispatchOrderRequest();
        request.setRequestId(null);

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(request));

        assertEquals("Request id is required.", error.getMessage());
    }

    @Test
    void createDispatchOrderRejectsNullRequestBody() {
        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(null));

        assertEquals("Request id is required.", error.getMessage());
    }

    @Test
    void createDispatchOrderRejectsPendingStatus() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.PENDING);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertEquals(
                "Only approved requests with sufficient warehouse stock can be dispatched. "
                        + "Requests awaiting stock must wait for supplier replenishment.",
                error.getMessage());
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    void createDispatchOrderRejectsDispatchingStatus() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.DISPATCHING);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertEquals(
                "Only approved requests with sufficient warehouse stock can be dispatched. "
                        + "Requests awaiting stock must wait for supplier replenishment.",
                error.getMessage());
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    void createDispatchOrderRejectsInTransitOrReceived() {
        for (PurchaseRequestStatus status : List.of(
                PurchaseRequestStatus.IN_TRANSIT, PurchaseRequestStatus.RECEIVED)) {
            PurchaseRequestModel pr = purchaseRequest(20L, status);
            when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

            BadRequestException error = assertThrows(
                    BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

            assertEquals(
                    "Only approved requests with sufficient warehouse stock can be dispatched. "
                            + "Requests awaiting stock must wait for supplier replenishment.",
                    error.getMessage());
        }
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    void createDispatchOrderRejectsMissingRequest() {
        CreateDispatchOrderRequest request = createRequest(99L);
        when(purchaseRequestRepository.findById(99L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.createDispatchOrder(request));

        assertEquals("Request not found.", error.getMessage());
    }

    @Test
    void createDispatchOrderRejectsNonApprovedRequest() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.AWAITING_STOCK);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertTrue(error.getMessage().contains("Only approved requests"));
    }

    @Test
    void createDispatchOrderRejectsInsufficientWarehouseStock() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel detail = detail(20L, 10, 3);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(detail));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(3), any(ProductModel.class))).thenReturn(72);

        WarehouseInventoryModel inventory = warehouseStock(10, 10);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertTrue(error.getMessage().contains("Insufficient warehouse stock"));
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    void createDispatchOrderDeductsStockAndMarksDispatching() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel detail = detail(20L, 10, 2);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(detail));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = warehouseStock(10, 100);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchModel branch = new BranchModel();
        branch.setId(5L);
        branch.setName("District 1");
        branch.setArea("North");
        branch.setRoute("R1");
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch));

        UserModel actor = new UserModel();
        actor.setId(3L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(dispatchOrderRepository.save(any(DispatchOrderModel.class))).thenAnswer(inv -> {
            DispatchOrderModel saved = inv.getArgument(0);
            saved.setId(70L);
            return saved;
        });
        when(dispatchOrderRequestRepository.findByDispatchOrderId(70L)).thenReturn(List.of());
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");

        DispatchOrderResponse response = service.createDispatchOrder(createRequest(20L));

        assertEquals(70L, response.getId());
        assertEquals(DispatchStatus.PREPARING.name(), response.getStatus());
        assertEquals(52, inventory.getQuantity());
        assertEquals(PurchaseRequestStatus.DISPATCHING, pr.getStatus());
        verify(purchaseRequestRepository).save(pr);
        verify(dispatchOrderRequestRepository).save(any(DispatchOrderRequestModel.class));
    }

    @Test
    void createDispatchOrderSucceedsWhenExactStockEqualsNeed() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel detail = detail(20L, 10, 2);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(detail));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = warehouseStock(10, 48);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchModel branch = new BranchModel();
        branch.setId(5L);
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch));

        UserModel actor = new UserModel();
        actor.setId(3L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(dispatchOrderRepository.save(any(DispatchOrderModel.class))).thenAnswer(inv -> {
            DispatchOrderModel saved = inv.getArgument(0);
            saved.setId(71L);
            return saved;
        });
        when(dispatchOrderRequestRepository.findByDispatchOrderId(71L)).thenReturn(List.of());
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-71");

        DispatchOrderResponse response = service.createDispatchOrder(createRequest(20L));

        assertEquals(DispatchStatus.PREPARING.name(), response.getStatus());
        assertEquals(0, inventory.getQuantity());
        assertEquals(PurchaseRequestStatus.DISPATCHING, pr.getStatus());
    }

    @Test
    void createDispatchOrderRejectsWhenWarehouseInventoryRowMissing() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel detail = detail(20L, 10, 2);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(detail));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of());

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertEquals("Insufficient warehouse stock for: product 10 (need 48, have 0)", error.getMessage());
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    void createDispatchOrderMergesNeedForSameProductMultiLine() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel line1 = detail(20L, 10, 1);
        PurchaseRequestDetailModel line2 = detail(20L, 10, 1);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(line1, line2));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(1), any(ProductModel.class))).thenReturn(24);

        WarehouseInventoryModel inventory = warehouseStock(10, 40);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertEquals("Insufficient warehouse stock for: product 10 (need 48, have 40)", error.getMessage());
        verify(productPackagingService, times(2)).toBaseQty(eq(1), any(ProductModel.class));
        verify(dispatchOrderRepository, never()).save(any());
    }

    @Test
    void createDispatchOrderSetsPreparingAndLinksRequest() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel detail = detail(20L, 10, 2);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(detail));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = warehouseStock(10, 100);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchModel branch = new BranchModel();
        branch.setId(5L);
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch));

        UserModel actor = new UserModel();
        actor.setId(3L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(dispatchOrderRepository.save(any(DispatchOrderModel.class))).thenAnswer(inv -> {
            DispatchOrderModel saved = inv.getArgument(0);
            saved.setId(72L);
            return saved;
        });
        when(dispatchOrderRequestRepository.findByDispatchOrderId(72L)).thenReturn(List.of());
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-72");

        DispatchOrderResponse response = service.createDispatchOrder(createRequest(20L));

        assertEquals(DispatchStatus.PREPARING.name(), response.getStatus());
        assertEquals(PurchaseRequestStatus.DISPATCHING, pr.getStatus());

        ArgumentCaptor<DispatchOrderModel> orderCaptor = ArgumentCaptor.forClass(DispatchOrderModel.class);
        verify(dispatchOrderRepository).save(orderCaptor.capture());
        assertEquals(DispatchStatus.PREPARING, orderCaptor.getValue().getStatus());

        ArgumentCaptor<DispatchOrderRequestModel> linkCaptor =
                ArgumentCaptor.forClass(DispatchOrderRequestModel.class);
        verify(dispatchOrderRequestRepository).save(linkCaptor.capture());
        assertEquals(72L, linkCaptor.getValue().getDispatchOrderId());
        assertEquals(20L, linkCaptor.getValue().getPurchaseRequestId());
        verify(purchaseRequestRepository).save(pr);
    }

    @Test
    void createDispatchOrderFailsOnSecondCreateWhenAlreadyDispatching() {
        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(20L)).thenReturn(Optional.of(pr));

        PurchaseRequestDetailModel detail = detail(20L, 10, 2);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(20L)).thenReturn(List.of(detail));
        when(productRepository.findByIdInWithCategory(anyCollection())).thenReturn(List.of(product(10)));
        when(productPackagingService.toBaseQty(eq(2), any(ProductModel.class))).thenReturn(48);

        WarehouseInventoryModel inventory = warehouseStock(10, 100);
        when(warehouseInventoryRepository.findByProductIdIn(Set.of(10))).thenReturn(List.of(inventory));
        when(warehouseInventoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BranchModel branch = new BranchModel();
        branch.setId(5L);
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch));

        UserModel actor = new UserModel();
        actor.setId(3L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(dispatchOrderRepository.save(any(DispatchOrderModel.class))).thenAnswer(inv -> {
            DispatchOrderModel saved = inv.getArgument(0);
            saved.setId(73L);
            return saved;
        });
        when(dispatchOrderRequestRepository.findByDispatchOrderId(73L)).thenReturn(List.of());
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-73");

        service.createDispatchOrder(createRequest(20L));
        assertEquals(PurchaseRequestStatus.DISPATCHING, pr.getStatus());

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.createDispatchOrder(createRequest(20L)));

        assertEquals(
                "Only approved requests with sufficient warehouse stock can be dispatched. "
                        + "Requests awaiting stock must wait for supplier replenishment.",
                error.getMessage());
        verify(dispatchOrderRepository, times(1)).save(any(DispatchOrderModel.class));
    }

    @Test
    void getDispatchOrderThrowsWhenMissing() {
        when(dispatchOrderRepository.findById(88L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.getDispatchOrder(88L));

        assertEquals("Dispatch order not found.", error.getMessage());
    }

    @Test
    void updateStatusRejectsMissingOrder() {
        when(dispatchOrderRepository.findById(88L)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.updateStatus(88L, statusRequest("DELIVERING")));

        assertEquals("Dispatch order not found.", error.getMessage());
    }

    @Test
    void updateStatusRejectsInvalidStatus() {
        when(dispatchOrderRepository.findById(70L))
                .thenReturn(Optional.of(dispatchOrder(70L, DispatchStatus.PREPARING)));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateStatus(70L, statusRequest("FLYING")));

        assertEquals("Invalid dispatch status.", error.getMessage());
    }

    @Test
    void updateStatusRejectsBlankStatus() {
        when(dispatchOrderRepository.findById(70L))
                .thenReturn(Optional.of(dispatchOrder(70L, DispatchStatus.PREPARING)));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateStatus(70L, statusRequest("  ")));

        assertEquals("Status is required.", error.getMessage());
    }

    @Test
    void updateStatusRejectsBranchReceiptOnlyReceived() {
        when(dispatchOrderRepository.findById(70L))
                .thenReturn(Optional.of(dispatchOrder(70L, DispatchStatus.PREPARING)));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateStatus(70L, statusRequest("RECEIVED")));

        assertTrue(error.getMessage().contains("Delivered status is set automatically"));
    }

    @Test
    void updateStatusRejectsCompletedDispatchOrder() {
        when(dispatchOrderRepository.findById(70L))
                .thenReturn(Optional.of(dispatchOrder(70L, DispatchStatus.RECEIVED)));

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateStatus(70L, statusRequest("DELIVERING")));

        assertEquals("Completed dispatch orders cannot be changed.", error.getMessage());
    }

    @Test
    void updateStatusMovesToDeliveringAndSyncsPurchaseRequest() {
        DispatchOrderModel order = dispatchOrder(70L, DispatchStatus.PREPARING);
        when(dispatchOrderRepository.findById(70L)).thenReturn(Optional.of(order));

        DispatchOrderRequestModel link = new DispatchOrderRequestModel();
        link.setDispatchOrderId(70L);
        link.setPurchaseRequestId(20L);
        when(dispatchOrderRequestRepository.findByDispatchOrderId(70L)).thenReturn(List.of(link));

        PurchaseRequestModel pr = purchaseRequest(20L, PurchaseRequestStatus.DISPATCHING);
        when(purchaseRequestRepository.findAllById(List.of(20L))).thenReturn(List.of(pr));
        when(dispatchOrderRepository.save(any(DispatchOrderModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");
        when(dispatchMapper.toRequestNumber(any())).thenReturn("PR-20");
        when(purchaseRequestRepository.findAllById(any())).thenReturn(List.of(pr));
        when(detailRepository.findByPurchaseRequestIdIn(any())).thenReturn(List.of());
        when(branchRepository.findAllById(any())).thenReturn(List.of());

        DispatchOrderResponse response = service.updateStatus(70L, statusRequest("DELIVERING"));

        assertEquals(DispatchStatus.DELIVERING.name(), response.getStatus());
        assertEquals(PurchaseRequestStatus.IN_TRANSIT, pr.getStatus());
        verify(purchaseRequestRepository).saveAll(List.of(pr));
    }

    @Test
    void updateStatusReturnsUnchangedWhenTargetEqualsCurrent() {
        DispatchOrderModel order = dispatchOrder(70L, DispatchStatus.DELIVERING);
        when(dispatchOrderRepository.findById(70L)).thenReturn(Optional.of(order));
        when(dispatchOrderRequestRepository.findByDispatchOrderId(70L)).thenReturn(List.of());
        when(dispatchMapper.toDispatchNumber(any())).thenReturn("DO-70");

        DispatchOrderResponse response = service.updateStatus(70L, statusRequest("DELIVERING"));

        assertEquals(DispatchStatus.DELIVERING.name(), response.getStatus());
        verify(dispatchOrderRepository, never()).save(any());
    }

    private static CreateDispatchOrderRequest createRequest(Long requestId) {
        CreateDispatchOrderRequest request = new CreateDispatchOrderRequest();
        request.setRequestId(requestId);
        request.setShipperName("Nguyen Van Shipper");
        request.setShipperPhone("0909123456");
        return request;
    }

    private static UpdateDispatchStatusRequest statusRequest(String status) {
        UpdateDispatchStatusRequest request = new UpdateDispatchStatusRequest();
        request.setStatus(status);
        return request;
    }

    private static PurchaseRequestModel purchaseRequest(Long id, PurchaseRequestStatus status) {
        PurchaseRequestModel pr = new PurchaseRequestModel();
        pr.setId(id);
        pr.setBranchId(5L);
        pr.setStatus(status);
        return pr;
    }

    private static PurchaseRequestDetailModel detail(Long requestId, Integer productId, Integer qty) {
        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setPurchaseRequestId(requestId);
        detail.setProductId(productId);
        detail.setApprovedQuantity(qty);
        detail.setRequestedQty(qty);
        return detail;
    }

    private static ProductModel product(Integer id) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setCode("P" + id);
        product.setName("Product " + id);
        product.setUnit("bottle");
        return product;
    }

    private static WarehouseInventoryModel warehouseStock(Integer productId, int qty) {
        WarehouseInventoryModel inventory = new WarehouseInventoryModel();
        inventory.setProductId(productId);
        inventory.setQuantity(qty);
        return inventory;
    }

    private static DispatchOrderModel dispatchOrder(Long id, DispatchStatus status) {
        DispatchOrderModel order = new DispatchOrderModel();
        order.setId(id);
        order.setStatus(status);
        return order;
    }
}
