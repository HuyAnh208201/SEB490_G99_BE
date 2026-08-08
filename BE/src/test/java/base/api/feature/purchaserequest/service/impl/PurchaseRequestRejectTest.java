package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.dto.request.RejectPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.mapper.PurchaseRequestMapper;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PurchaseRequestServiceImpl#rejectRequest} — access control,
 * pending-only rule, reject reason validation, and REJECTED status transition.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRequestRejectTest {

    private static final Long REQUEST_ID = 200L;
    private static final Long BRANCH_ID = 10L;
    private static final Long APPROVER_ID = 50L;

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
    void rejectRequestDeniesAccessForInventoryStaff() {
        signedInAs(UserRole.INVENTORY_STAFF, BRANCH_ID);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Not needed")));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).findById(anyLong());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestRejectsNonPendingStatus() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        request.setStatus(PurchaseRequestStatus.DRAFT);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Too late")));

        assertTrue(error.getMessage().contains("Only pending requests can be rejected."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestRequiresReasonWhenNull() {
        signedInAs(UserRole.ADMIN, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, null));

        assertTrue(error.getMessage().contains("Reject reason is required."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestRequiresReasonWhenBlank() {
        signedInAs(UserRole.DIRECTOR, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("   ")));

        assertTrue(error.getMessage().contains("Reject reason is required."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestSetsRejectedStatusOnSuccess() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        PurchaseRequestResponse response =
                service.rejectRequest(REQUEST_ID, rejectRequest("Insufficient justification"));

        assertEquals(PurchaseRequestStatus.REJECTED, request.getStatus());
        assertEquals("Insufficient justification", request.getRejectReason());
        assertEquals(APPROVER_ID, request.getApprovedBy());
        assertNotNull(request.getApprovedAt());
        assertEquals(REQUEST_ID, response.getId());
        verify(purchaseRequestRepository).save(request);
    }

    @Test
    void rejectRequestThrowsWhenRequestMissing() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Not found case")));

        assertTrue(error.getMessage().contains("Request not found."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestDeniesBranchManager() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Branch cannot reject")));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).findById(anyLong());
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestSucceedsForAdmin() {
        signedInAs(UserRole.ADMIN, null);
        PurchaseRequestModel request = pendingRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        PurchaseRequestResponse response =
                service.rejectRequest(REQUEST_ID, rejectRequest("Admin rejection"));

        assertEquals(PurchaseRequestStatus.REJECTED, request.getStatus());
        assertEquals("Admin rejection", request.getRejectReason());
        assertEquals(APPROVER_ID, request.getApprovedBy());
        assertNotNull(request.getApprovedAt());
        assertEquals(REQUEST_ID, response.getId());
        verify(purchaseRequestRepository).save(request);
    }

    @Test
    void rejectRequestRejectsNullStatus() {
        signedInAs(UserRole.DIRECTOR, null);
        PurchaseRequestModel request = pendingRequest();
        request.setStatus(null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Null status")));

        assertTrue(error.getMessage().contains("Only pending requests can be rejected."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestRejectsAlreadyRejected() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        request.setStatus(PurchaseRequestStatus.REJECTED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Already rejected")));

        assertTrue(error.getMessage().contains("Only pending requests can be rejected."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestRejectsApprovedStatus() {
        signedInAs(UserRole.ADMIN, null);
        PurchaseRequestModel request = pendingRequest();
        request.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Already approved")));

        assertTrue(error.getMessage().contains("Only pending requests can be rejected."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestTrimsReasonAndPersists() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        service.rejectRequest(REQUEST_ID, rejectRequest("  trimmed reason  "));

        assertEquals(PurchaseRequestStatus.REJECTED, request.getStatus());
        assertEquals("trimmed reason", request.getRejectReason());
        verify(purchaseRequestRepository).save(request);
    }

    @Test
    void rejectRequestRejectsEmptyStringReason() {
        signedInAs(UserRole.DIRECTOR, null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(pendingRequest()));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("")));

        assertTrue(error.getMessage().contains("Reject reason is required."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void rejectRequestFailsOnDoubleReject() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);
        PurchaseRequestModel request = pendingRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(purchaseRequestRepository.save(any(PurchaseRequestModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        stubBuildResponse();

        service.rejectRequest(REQUEST_ID, rejectRequest("First reject"));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.rejectRequest(REQUEST_ID, rejectRequest("Second reject")));

        assertTrue(error.getMessage().contains("Only pending requests can be rejected."));
        verify(purchaseRequestRepository, times(1)).save(any());
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
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of());
        PurchaseRequestResponse response = new PurchaseRequestResponse();
        response.setId(REQUEST_ID);
        when(purchaseRequestMapper.toResponse(any(), any(), any(), any(), any(), any(), any()))
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

    private static RejectPurchaseRequestRequest rejectRequest(String reason) {
        RejectPurchaseRequestRequest request = new RejectPurchaseRequestRequest();
        request.setReason(reason);
        return request;
    }
}
