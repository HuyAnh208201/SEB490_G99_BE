package base.api.feature.purchaserequest.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.dispatch.service.WarehouseStockAllocationHelper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.dto.request.CreatePurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.request.PurchaseRequestItemRequest;
import base.api.feature.purchaserequest.dto.request.SaveDraftRequest;
import base.api.feature.purchaserequest.dto.request.SubmitPurchaseRequestRequest;
import base.api.feature.purchaserequest.dto.response.PurchaseRequestResponse;
import base.api.feature.purchaserequest.mapper.PurchaseRequestMapper;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptItemRepository;
import base.api.feature.purchaserequest.repository.GoodsReceiptRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestDetailRepository;
import base.api.feature.purchaserequest.repository.PurchaseRequestRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.ProductModel;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Extra lifecycle coverage for createDraft / saveDraft / submitRequest / cancelRequest
 * beyond {@link PurchaseRequestLifecycleTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PurchaseRequestSubmitTest {

    private static final Long REQUEST_ID = 400L;
    private static final Long BRANCH_ID = 10L;
    private static final Long USER_ID = 40L;
    private static final Integer PRODUCT_ID = 7;

    @Mock private PurchaseRequestRepository purchaseRequestRepository;
    @Mock private PurchaseRequestDetailRepository detailRepository;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private WarehouseInventoryRepository warehouseInventoryRepository;
    @Mock private WarehouseStockAllocationHelper warehouseStockAllocationHelper;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Mock private IProductRepository productRepository;
    @Mock private ProductPackagingService productPackagingService;
    @Mock private IBranchRepository branchRepository;
    @Mock private IUserRepository userRepository;
    @Mock private PurchaseRequestMapper purchaseRequestMapper;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PurchaseRequestServiceImpl service;

    @Test
    void createDraftSucceedsWithEmptyItems() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });
        stubEmptyDetailsForBuild();
        stubBuildResponse();

        PurchaseRequestResponse response = service.createDraft(new CreatePurchaseRequestRequest());

        assertEquals(REQUEST_ID, response.getId());
        ArgumentCaptor<PurchaseRequestModel> saved = ArgumentCaptor.forClass(PurchaseRequestModel.class);
        verify(purchaseRequestRepository).save(saved.capture());
        assertEquals(PurchaseRequestStatus.DRAFT, saved.getValue().getStatus());
        assertEquals(BRANCH_ID, saved.getValue().getBranchId());
        verify(detailRepository).deleteByPurchaseRequestId(REQUEST_ID);
        verify(detailRepository, never()).saveAll(any());
    }

    @Test
    void createDraftTrimsBlankNotesToNull() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });
        stubEmptyDetailsForBuild();
        stubBuildResponse();

        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        body.setNotes("   ");

        service.createDraft(body);

        ArgumentCaptor<PurchaseRequestModel> saved = ArgumentCaptor.forClass(PurchaseRequestModel.class);
        verify(purchaseRequestRepository).save(saved.capture());
        assertNull(saved.getValue().getReason());
    }

    @Test
    void createDraftDeniesWarehouseManager() {
        signedInAs(UserRole.WAREHOUSE_MANAGER, null);

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.createDraft(new CreatePurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void createDraftRejectsNullProductId() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });

        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        PurchaseRequestItemRequest item = new PurchaseRequestItemRequest();
        item.setProductId(null);
        item.setRequestedQty(BigDecimal.ONE);
        body.setItems(List.of(item));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createDraft(body));

        assertTrue(error.getMessage().contains("Product is required."));
    }

    @Test
    void createDraftRejectsZeroQuantity() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });

        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        body.setItems(List.of(item(PRODUCT_ID, "0")));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createDraft(body));

        assertTrue(error.getMessage().contains("Quantity must be greater than zero."));
    }

    @Test
    void createDraftRejectsDecimalQuantity() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });

        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        body.setItems(List.of(item(PRODUCT_ID, "1.5")));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createDraft(body));

        assertTrue(error.getMessage().contains("Quantity must be an integer."));
    }

    @Test
    void createDraftRejectsNullQuantity() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });

        PurchaseRequestItemRequest line = new PurchaseRequestItemRequest();
        line.setProductId(PRODUCT_ID);
        line.setRequestedQty(null);
        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        body.setItems(List.of(line));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.createDraft(body));

        assertTrue(error.getMessage().contains("Quantity is required."));
    }

    @Test
    void createDraftRejectsInactiveProduct() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });
        ProductModel product = activeProduct();
        product.setStatus("inactive");
        when(productRepository.findByIdInWithCategory(any())).thenReturn(List.of(product));

        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        body.setItems(List.of(item(PRODUCT_ID, "2")));

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.createDraft(body));

        assertTrue(error.getMessage().contains("Product not found."));
    }

    @Test
    void createDraftPersistsActiveProductLine() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> {
            PurchaseRequestModel model = call.getArgument(0);
            model.setId(REQUEST_ID);
            return model;
        });
        when(productRepository.findByIdInWithCategory(any())).thenReturn(List.of(activeProduct()));
        when(purchaseRequestMapper.buildDetailSnapshot(anyLong(), any(), any()))
                .thenAnswer(call -> {
                    PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
                    detail.setPurchaseRequestId(call.getArgument(0));
                    detail.setProductId(PRODUCT_ID);
                    detail.setRequestedQty(call.getArgument(2));
                    return detail;
                });
        stubEmptyDetailsForBuild();
        stubBuildResponse();

        CreatePurchaseRequestRequest body = new CreatePurchaseRequestRequest();
        body.setNotes("Need restock");
        body.setItems(List.of(item(PRODUCT_ID, "3")));

        service.createDraft(body);

        verify(detailRepository).saveAll(any());
        ArgumentCaptor<PurchaseRequestModel> saved = ArgumentCaptor.forClass(PurchaseRequestModel.class);
        verify(purchaseRequestRepository).save(saved.capture());
        assertEquals("Need restock", saved.getValue().getReason());
    }

    @Test
    void saveDraftRejectsMissingRequest() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.saveDraft(REQUEST_ID, new SaveDraftRequest()));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void saveDraftRejectsPendingStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.PENDING);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.saveDraft(REQUEST_ID, new SaveDraftRequest()));

        assertTrue(error.getMessage().contains("Request already submitted."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void saveDraftRejectsApprovedStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.saveDraft(REQUEST_ID, new SaveDraftRequest()));

        assertTrue(error.getMessage().contains("Cannot edit submitted request."));
    }

    @Test
    void saveDraftRejectsWrongBranch() {
        signedInAs(UserRole.BRANCH_MANAGER, 99L);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.saveDraft(REQUEST_ID, new SaveDraftRequest()));

        assertTrue(error.getMessage().contains("Access denied."));
    }

    @Test
    void saveDraftUpdatesNotesOnEditableDraft() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        stubEmptyDetailsForBuild();
        stubBuildResponse();

        SaveDraftRequest body = new SaveDraftRequest();
        body.setNotes("Updated notes");

        PurchaseRequestResponse response = service.saveDraft(REQUEST_ID, body);

        assertEquals(REQUEST_ID, response.getId());
        assertEquals("Updated notes", request.getReason());
        verify(detailRepository).deleteByPurchaseRequestId(REQUEST_ID);
    }

    @Test
    void submitRequestSucceedsWithPositiveQuantities() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(PRODUCT_ID, 5)));
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        stubBuildResponse();

        PurchaseRequestResponse response =
                service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest());

        assertEquals(PurchaseRequestStatus.PENDING, request.getStatus());
        assertEquals(REQUEST_ID, response.getId());
    }

    @Test
    void submitRequestRejectsNonPositiveDetailQuantity() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 0);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Quantity must be greater than zero."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsNullDetailQuantity() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));
        PurchaseRequestDetailModel line = detail(PRODUCT_ID, 1);
        line.setRequestedQty(null);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of(line));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Quantity must be greater than zero."));
    }

    @Test
    void submitRequestRejectsAlreadyPending() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.PENDING);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Request already submitted."));
    }

    @Test
    void submitRequestRejectsMissingRequest() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));
    }

    @Test
    void submitRequestDeniesAccessForWrongBranch() {
        signedInAs(UserRole.BRANCH_MANAGER, 99L);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsApprovedStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Cannot edit submitted request."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsRejectedStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.REJECTED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Cannot edit submitted request."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsCancelledStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.CANCELLED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        ForbiddenException error = assertThrows(
                ForbiddenException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Cannot edit submitted request."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsNegativeDetailQuantity() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(PRODUCT_ID, -1)));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Quantity must be greater than zero."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestRejectsMixedValidAndNonPositiveQty() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));
        PurchaseRequestDetailModel valid = detail(PRODUCT_ID, 5);
        PurchaseRequestDetailModel invalid = detail(PRODUCT_ID + 1, 0);
        invalid.setId(2L);
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(valid, invalid));

        BadRequestException error = assertThrows(
                BadRequestException.class,
                () -> service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest()));

        assertTrue(error.getMessage().contains("Quantity must be greater than zero."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void submitRequestSucceedsForInventoryStaffSameBranch() {
        signedInAs(UserRole.INVENTORY_STAFF, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(PRODUCT_ID, 3)));
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        stubBuildResponse();

        PurchaseRequestResponse response =
                service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest());

        assertEquals(PurchaseRequestStatus.PENDING, request.getStatus());
        assertEquals(REQUEST_ID, response.getId());
    }

    @Test
    void submitRequestSavesPendingStatusOnce() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID))
                .thenReturn(List.of(detail(PRODUCT_ID, 2)));
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        stubBuildResponse();

        service.submitRequest(REQUEST_ID, new SubmitPurchaseRequestRequest());

        ArgumentCaptor<PurchaseRequestModel> saved = ArgumentCaptor.forClass(PurchaseRequestModel.class);
        verify(purchaseRequestRepository, times(1)).save(saved.capture());
        assertEquals(PurchaseRequestStatus.PENDING, saved.getValue().getStatus());
    }

    @Test
    void cancelRequestSucceedsFromDraft() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));
        when(purchaseRequestRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        stubEmptyDetailsForBuild();
        stubBuildResponse();

        PurchaseRequestResponse response = service.cancelRequest(REQUEST_ID);

        assertEquals(PurchaseRequestStatus.CANCELLED, request.getStatus());
        assertEquals(REQUEST_ID, response.getId());
    }

    @Test
    void cancelRequestRejectsMissingRequest() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.cancelRequest(REQUEST_ID));
    }

    @Test
    void cancelRequestRejectsWrongBranch() {
        signedInAs(UserRole.BRANCH_MANAGER, 99L);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(draftRequest()));

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.cancelRequest(REQUEST_ID));

        assertTrue(error.getMessage().contains("Access denied."));
        verify(purchaseRequestRepository, never()).save(any());
    }

    @Test
    void cancelRequestRejectsNullStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(null);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.cancelRequest(REQUEST_ID));

        assertTrue(error.getMessage().contains("Only draft requests can be cancelled."));
    }

    @Test
    void cancelRequestRejectsApprovedStatus() {
        signedInAs(UserRole.BRANCH_MANAGER, BRANCH_ID);
        PurchaseRequestModel request = draftRequest();
        request.setStatus(PurchaseRequestStatus.APPROVED);
        when(purchaseRequestRepository.findById(REQUEST_ID)).thenReturn(Optional.of(request));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.cancelRequest(REQUEST_ID));

        assertTrue(error.getMessage().contains("Only draft requests can be cancelled."));
    }

    private void signedInAs(UserRole role, Long branchId) {
        UserModel user = new UserModel();
        user.setId(USER_ID);
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

    private void stubEmptyDetailsForBuild() {
        when(detailRepository.findByPurchaseRequestIdOrderByIdAsc(REQUEST_ID)).thenReturn(List.of());
    }

    private static PurchaseRequestModel draftRequest() {
        PurchaseRequestModel request = new PurchaseRequestModel();
        request.setId(REQUEST_ID);
        request.setBranchId(BRANCH_ID);
        request.setCreatedBy(USER_ID);
        request.setStatus(PurchaseRequestStatus.DRAFT);
        return request;
    }

    private static PurchaseRequestDetailModel detail(Integer productId, int qty) {
        PurchaseRequestDetailModel detail = new PurchaseRequestDetailModel();
        detail.setId(1L);
        detail.setPurchaseRequestId(REQUEST_ID);
        detail.setProductId(productId);
        detail.setRequestedQty(qty);
        return detail;
    }

    private static PurchaseRequestItemRequest item(Integer productId, String qty) {
        PurchaseRequestItemRequest item = new PurchaseRequestItemRequest();
        item.setProductId(productId);
        item.setRequestedQty(new BigDecimal(qty));
        return item;
    }

    private static ProductModel activeProduct() {
        ProductModel product = new ProductModel();
        product.setId(PRODUCT_ID);
        product.setName("Milk");
        product.setStatus("active");
        return product;
    }
}
