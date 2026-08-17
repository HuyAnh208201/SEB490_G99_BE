package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.dto.request.ApprovePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.mapper.PurchaseRequestMapper;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.PurchaseRequestDetailModel;
import base.api.shared.entity.PurchaseRequestModel;
import base.api.shared.entity.UserModel;
import base.api.shared.enums.PurchaseRequestStatus;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ForbiddenException;
import base.api.shared.exception.NotFoundException;
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PurchaseRequestServiceImpl#approveRequest} — access control,
 * pending/empty/quantity validation, warehouse stock routing, and approve-item defaults.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRequestApproveTest {

    private static final Long REQUEST_ID = 100L;
    private static final Long BRANCH_ID = 10L;
    private static final Long APPROVER_ID = 50L;
    private static final Integer PRODUCT_ID = 7;

    @Mock
    private PurchaseRequestRepository purchaseRequestRepository;

    @Mock
    private PurchaseRequestDetailRepository detailRepository;

    @Mock
    private BranchInventoryRepository branchInventoryRepository;

    @Mock
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Mock
    private WarehouseStockAllocationHelper warehouseStockAllocationHelper;

    @Mock
    private GoodsReceiptRepository goodsReceiptRepository;

    @Mock
    private GoodsReceiptItemRepository goodsReceiptItemRepository;

    @Mock
    private IProductRepository productRepository;

    @Mock
    private ProductPackagingService productPackagingService;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private PurchaseRequestMapper purchaseRequestMapper;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PurchaseRequestServiceImpl service;

    @Test
    void approveRequestDeniesAccessForBranchManager() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 5)));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).findById(anyLong());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestThrowsWhenRequestNotFound() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 5)));

        assertTrue(error.getMessage().contains("Request not found."));
        verify(detailRepository, never()).saveAll(any());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestDeniesAccessForAdmin() {
        signedInAs(UserRole.ADMIN, null);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 5)));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).findById(anyLong());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestDeniesAccessForDirector() {
        signedInAs(UserRole.DIRECTOR, null);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 5)));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).findById(anyLong());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestRejectsNonPendingStatus() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        request.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 5)));

        assertTrue(error.getMessage().contains("Only pending requests can be approved."));
        verify(detailRepository, never()).saveAll(any());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestRejectsEmptyDetails() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of());

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 5)));

        assertTrue(error.getMessage().contains("Request has no items to approve."));
        verify(detailRepository, never()).saveAll(any());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestRejectsNegativeApprovedQuantity() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(PRODUCT_ID, 10)));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, -1)));

        assertTrue(error.getMessage().contains("Approved quantity must be zero or greater."));
        verify(detailRepository, never()).saveAll(any());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestRejectsApprovedGreaterThanRequested() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(PRODUCT_ID, 10)));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 11)));

        assertTrue(error.getMessage().contains("Approved quantity cannot exceed requested quantity."));
        verify(detailRepository, never()).saveAll(any());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void approveRequestSetsApprovedWhenWarehouseHasEnoughStock() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 10);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));
        when(warehouseStockAllocationHelper.canApproveRequest(eq(REQUEST_ID), any())).thenReturn(true);
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        PurchaseRequestResponse response =
                service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 8));

        assertEquals(PurchaseRequestStatus.APPROVED, request.getStatus());
        assertEquals(8, line.getApprovedQuantity());
        assertEquals(APPROVER_ID, request.getApprovedBy());
        assertNotNull(request.getApprovedAt());
        assertNull(request.getRejectReason());
        assertEquals(REQUEST_ID, response.getId());
        verify(detailRepository).saveAll(any());
        verify(warehouseStockAllocationHelper).reconcileApprovedStockStatus();
    }

    @Test
    void approveRequestSetsAwaitingStockWhenWarehouseLacksStock() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 10);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));
        when(warehouseStockAllocationHelper.canApproveRequest(eq(REQUEST_ID), any())).thenReturn(false);
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 10));

        assertEquals(PurchaseRequestStatus.AWAITING_STOCK, request.getStatus());
        verify(warehouseStockAllocationHelper).reconcileApprovedStockStatus();
    }

    @Test
    void approveRequestDefaultsToRequestedQtyWhenItemsNull() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 12);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));
        when(warehouseStockAllocationHelper.canApproveRequest(eq(REQUEST_ID), any())).thenReturn(true);
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        ApprovePurchaseRequestRequest body = new ApprovePurchaseRequestRequest();
        body.setItems(null);

        service.approveRequest(REQUEST_ID, body);

        assertEquals(12, line.getApprovedQuantity());
        assertEquals(PurchaseRequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void approveRequestDefaultsToRequestedQtyWhenItemsEmpty() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 9);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));
        when(warehouseStockAllocationHelper.canApproveRequest(eq(REQUEST_ID), any())).thenReturn(true);
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        service.approveRequest(REQUEST_ID, new ApprovePurchaseRequestRequest());

        assertEquals(9, line.getApprovedQuantity());
    }

    @Test
    void approveRequestAllowsZeroApprovedQuantity() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 5);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));
        when(warehouseStockAllocationHelper.canApproveRequest(eq(REQUEST_ID), any())).thenReturn(true);
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        service.approveRequest(REQUEST_ID, approveRequest(PRODUCT_ID, 0));

        assertEquals(0, line.getApprovedQuantity());
        assertEquals(PurchaseRequestStatus.APPROVED, request.getStatus());
    }

    @Test
    void approveRequestSkipsNullItemAndNullProductId() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 15);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));
        when(warehouseStockAllocationHelper.canApproveRequest(eq(REQUEST_ID), any())).thenReturn(true);
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        ApprovePurchaseRequestRequest body = new ApprovePurchaseRequestRequest();
        List<ApprovePurchaseRequestRequest.ApproveItem> items = new ArrayList<>();
        items.add(null);
        ApprovePurchaseRequestRequest.ApproveItem missingProduct = new ApprovePurchaseRequestRequest.ApproveItem();
        missingProduct.setProductId(null);
        missingProduct.setApprovedQuantity(1);
        items.add(missingProduct);
        body.setItems(items);

        service.approveRequest(REQUEST_ID, body);

        assertEquals(15, line.getApprovedQuantity());
        verify(detailRepository).saveAll(any());
    }

    private void signedInAs(UserRole role, Long branchId) {
        UserModel user = new UserModel();
        user.setId(APPROVER_ID);
        user.setBranchId(branchId);
        user.setRole(role);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(role);
    }

    private void stubBuildResponse() {
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());
        when(userRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(warehouseInventoryRepository.findByProductIdIn(any())).thenReturn(List.of());
        PurchaseRequestResponse response = new PurchaseRequestResponse();
        response.setId(REQUEST_ID);
        when(purchaseRequestMapper.toResponse(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(response);
    }

    private static PurchaseRequestModel pendingRequest() {
        PurchaseRequestModel request = new PurchaseRequestModel();
        request.setId(REQUEST_ID);
        request.setBranchId(BRANCH_ID);
        request.setCreatedBy(1L);
        request.setStatus(PurchaseRequestStatus.PENDING);
        return request;
    }

    private static PurchaseRequestDetailModel detail(Integer productId, int requestedQty) {
        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setId(1L);
        detail.setPurchaseRequestId(REQUEST_ID);
        detail.setProductId(productId);
        detail.setRequestedQty(requestedQty);
        return detail;
    }

    private static ApprovePurchaseRequestRequest approveRequest(Integer productId, Integer approvedQty) {
        ApprovePurchaseRequestRequest request = new ApprovePurchaseRequestRequest();
        ApprovePurchaseRequestRequest.ApproveItem item = new ApprovePurchaseRequestRequest.ApproveItem();
        item.setProductId(productId);
        item.setApprovedQuantity(approvedQty);
        request.setItems(List.of(item));
        return request;
    }
}
