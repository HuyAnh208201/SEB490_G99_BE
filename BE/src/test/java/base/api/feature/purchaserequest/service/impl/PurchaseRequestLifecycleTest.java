package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.ReceiveGoodsRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
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
import base.api.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for draft/submit/cancel/receive lifecycle guards on
 * {@link PurchaseRequestServiceImpl}.
 */
@ExtendWith(MockitoExtension.class)
class PurchaseRequestLifecycleTest {

    private static final Long REQUEST_ID = 300L;
    private static final Long BRANCH_ID = 10L;
    private static final Long USER_ID = 40L;

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
    void createDraftDeniesBranchManagerWithoutBranch() {
        UserModel user = new UserModel();
        user.setId(USER_ID);
        user.setBranchId(null);
        user.setRole(UserRole.BRANCH_MANAGER);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.createDraft(new CreatePurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsEmptyDetails() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of());

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Cannot submit empty request."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void cancelRequestRejectsNonDraftStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.PENDING);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.cancelRequest(REQUEST_ID));

        assertTrue(error.getMessage().contains("Only draft requests can be cancelled."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void receiveGoodsRejectsDirectReceiveForInventoryStaff() {
        signedInAs(UserRole.INVENTORY_STAFF, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.receiveGoods(REQUEST_ID, new ReceiveGoodsRequest()));

        assertTrue(error.getMessage().contains(
                "Direct receive is disabled. Use Order Tracking to receive shipments in transit."));
        verify(goodsReceiptRepository, never()).save(any());
        verify(purchaseRequestRepository, never()).save(any());
    }

    private void signedInAs(UserRole role, Long branchId) {
        UserModel user = new UserModel();
        user.setId(USER_ID);
        user.setBranchId(branchId);
        user.setRole(role);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(role);
    }

    private static PurchaseRequestModel draftRequest() {
        PurchaseRequestModel request = new PurchaseRequestModel();
        request.setId(REQUEST_ID);
        request.setBranchId(BRANCH_ID);
        request.setCreatedBy(USER_ID);
        request.setStatus(PurchaseRequestStatus.DRAFT);
        return request;
    }
}
