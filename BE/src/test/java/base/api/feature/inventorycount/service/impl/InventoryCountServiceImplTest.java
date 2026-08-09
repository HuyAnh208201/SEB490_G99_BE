package base.api.feature.inventorycount.service.impl;

import base.api.feature.auth.repository.IUserRepository;
import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventorycount.dto.request.SubmitInventoryCountRequest;
import base.api.feature.inventorycount.dto.response.InventoryCountSessionResponse;
import base.api.feature.inventorycount.repository.InventoryCountItemRepository;
import base.api.feature.inventorycount.repository.InventoryCountSessionRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.InventoryCountItemModel;
import base.api.shared.entity.InventoryCountSessionModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryCountServiceImpl} submit / approve / reject flows.
 */
@ExtendWith(MockitoExtension.class)
class InventoryCountServiceImplTest {

    private static final Long BRANCH_ID = 10L;
    private static final Long SESSION_ID = 50L;
    private static final Integer PRODUCT_ID = 7;

    @Mock
    private InventoryCountSessionRepository sessionRepository;

    @Mock
    private InventoryCountItemRepository itemRepository;

    @Mock
    private BranchInventoryRepository branchInventoryRepository;

    @Mock
    private IProductRepository productRepository;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private IUserRepository userRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private InventoryCountServiceImpl service;

    @Test
    void submitCountCreatesPendingSessionAndItems() {
        asStaff(BRANCH_ID);
        BranchInventoryModel stock = stockRow(PRODUCT_ID, 8);
        when(branchInventoryRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of(stock));
        ProductModel product = product(PRODUCT_ID, "P-007", "Cola");
        when(productRepository.findByIdInWithCategory(Set.of(PRODUCT_ID))).thenReturn(List.of(product));
        when(sessionRepository.save(any(InventoryCountSessionModel.class))).thenAnswer(inv -> {
            InventoryCountSessionModel session = inv.getArgument(0);
            session.setId(SESSION_ID);
            return session;
        });
        when(itemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        SubmitInventoryCountRequest request = submitRequest(PRODUCT_ID, 10, "short");
        InventoryCountSessionResponse response = service.submitCount(request);

        assertEquals("PENDING_APPROVAL", response.getStatus());
        assertEquals(1, response.getTotalProducts());
        ArgumentCaptor<InventoryCountSessionModel> sessionCaptor =
                ArgumentCaptor.forClass(InventoryCountSessionModel.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        assertEquals(BRANCH_ID, sessionCaptor.getValue().getBranchId());
        assertEquals("PENDING_APPROVAL", sessionCaptor.getValue().getStatus());
        verify(itemRepository).saveAll(anyList());
    }

    @Test
    void submitCountRejectsEmptyItems() {
        SubmitInventoryCountRequest request = new SubmitInventoryCountRequest();

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.submitCount(request));

        assertEquals("At least one counted item is required.", error.getMessage());
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void submitCountRejectsNullRequest() {
        BadRequestException error = assertThrows(BadRequestException.class, () -> service.submitCount(null));

        assertEquals("At least one counted item is required.", error.getMessage());
    }

    @Test
    void submitCountRejectsStaffWithoutBranch() {
        asStaff(null);
        SubmitInventoryCountRequest request = submitRequest(PRODUCT_ID, 1, null);

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.submitCount(request));

        assertEquals("Your account is not assigned to a branch.", error.getMessage());
    }

    @Test
    void submitCountRejectsUnknownProduct() {
        asStaff(BRANCH_ID);
        when(branchInventoryRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of());
        when(productRepository.findByIdInWithCategory(Set.of(PRODUCT_ID))).thenReturn(List.of());
        when(sessionRepository.save(any(InventoryCountSessionModel.class))).thenAnswer(inv -> {
            InventoryCountSessionModel session = inv.getArgument(0);
            session.setId(SESSION_ID);
            return session;
        });

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.submitCount(submitRequest(PRODUCT_ID, 1, null)));

        assertEquals("Invalid product in count sheet.", error.getMessage());
    }

    @Test
    void approveAppliesCountedStockAndMarksApproved() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        InventoryCountItemModel item = countItem(SESSION_ID, PRODUCT_ID, 8, 10);
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(item));
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(branchInventoryRepository.save(any(BranchInventoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(session)).thenReturn(session);
        when(productRepository.findByIdInWithCategory(Set.of(PRODUCT_ID)))
                .thenReturn(List.of(product(PRODUCT_ID, "P-007", "Cola")));
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        InventoryCountSessionResponse response = service.approve(SESSION_ID);

        assertEquals("APPROVED", response.getStatus());
        ArgumentCaptor<BranchInventoryModel> stockCaptor = ArgumentCaptor.forClass(BranchInventoryModel.class);
        verify(branchInventoryRepository).save(stockCaptor.capture());
        assertEquals(10, stockCaptor.getValue().getCurrentStock());
        assertEquals("APPROVED", session.getStatus());
    }

    @Test
    void approveRejectsNonPendingSession() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        session.setStatus("APPROVED");
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.approve(SESSION_ID));

        assertEquals("Only pending sessions can be approved.", error.getMessage());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void rejectMarksSessionRejectedWithoutStockChange() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(session)).thenReturn(session);
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        InventoryCountSessionResponse response = service.reject(SESSION_ID);

        assertEquals("REJECTED", response.getStatus());
        assertEquals("REJECTED", session.getStatus());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void rejectRejectsNonPendingSession() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        session.setStatus("REJECTED");
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.reject(SESSION_ID));

        assertEquals("Only pending sessions can be rejected.", error.getMessage());
    }

    @Test
    void approveThrowsWhenSessionMissing() {
        asStaff(BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(NotFoundException.class, () -> service.approve(SESSION_ID));

        assertEquals("Inventory count session not found.", error.getMessage());
    }

    @Test
    void approveForbiddenForOtherBranchSession() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, 99L);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.approve(SESSION_ID));

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void getSessionReturnsDetailForOwnBranch() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        InventoryCountSessionResponse response = service.getSession(SESSION_ID);

        assertEquals(SESSION_ID, response.getId());
        assertEquals("PENDING_APPROVAL", response.getStatus());
    }

    @Test
    void approveThrowsWhenUserHasNoBranch() {
        asStaff(null);

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.approve(SESSION_ID));

        assertEquals("Your account is not assigned to a branch.", error.getMessage());
        verify(sessionRepository, never()).findById(any());
    }

    @Test
    void approveRejectsRejectedSession() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        session.setStatus("REJECTED");
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.approve(SESSION_ID));

        assertEquals("Only pending sessions can be approved.", error.getMessage());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void approveSucceedsWithEmptyItems() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        session.setTotalProducts(0);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());
        when(sessionRepository.save(session)).thenReturn(session);
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        InventoryCountSessionResponse response = service.approve(SESSION_ID);

        assertEquals("APPROVED", response.getStatus());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void approveSetsStockToZeroWhenCountedQtyZero() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        InventoryCountItemModel item = countItem(SESSION_ID, PRODUCT_ID, 8, 0);
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(item));
        BranchInventoryModel existing = stockRow(PRODUCT_ID, 8);
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                .thenReturn(Optional.of(existing));
        when(branchInventoryRepository.save(any(BranchInventoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(session)).thenReturn(session);
        when(productRepository.findByIdInWithCategory(Set.of(PRODUCT_ID)))
                .thenReturn(List.of(product(PRODUCT_ID, "P-007", "Cola")));
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        service.approve(SESSION_ID);

        ArgumentCaptor<BranchInventoryModel> stockCaptor = ArgumentCaptor.forClass(BranchInventoryModel.class);
        verify(branchInventoryRepository).save(stockCaptor.capture());
        assertEquals(0, stockCaptor.getValue().getCurrentStock());
    }

    @Test
    void approveSkipsNegativeCountedQty() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        InventoryCountItemModel item = countItem(SESSION_ID, PRODUCT_ID, 8, -3);
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(item));
        when(sessionRepository.save(session)).thenReturn(session);
        when(productRepository.findByIdInWithCategory(Set.of(PRODUCT_ID)))
                .thenReturn(List.of(product(PRODUCT_ID, "P-007", "Cola")));
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        InventoryCountSessionResponse response = service.approve(SESSION_ID);

        assertEquals("APPROVED", response.getStatus());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void approveSkipsNullProductId() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        InventoryCountItemModel nullProduct = countItem(SESSION_ID, null, 8, 10);
        InventoryCountItemModel valid = countItem(SESSION_ID, PRODUCT_ID, 8, 12);
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of(nullProduct, valid));
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, PRODUCT_ID))
                .thenReturn(Optional.empty());
        when(branchInventoryRepository.save(any(BranchInventoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sessionRepository.save(session)).thenReturn(session);
        when(productRepository.findByIdInWithCategory(Set.of(PRODUCT_ID)))
                .thenReturn(List.of(product(PRODUCT_ID, "P-007", "Cola")));
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        InventoryCountSessionResponse response = service.approve(SESSION_ID);

        assertEquals("APPROVED", response.getStatus());
        ArgumentCaptor<BranchInventoryModel> stockCaptor = ArgumentCaptor.forClass(BranchInventoryModel.class);
        verify(branchInventoryRepository).save(stockCaptor.capture());
        assertEquals(PRODUCT_ID, stockCaptor.getValue().getProductId());
        assertEquals(12, stockCaptor.getValue().getCurrentStock());
    }

    @Test
    void approveSetsReviewedByAndReviewedAt() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());
        when(sessionRepository.save(session)).thenReturn(session);
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        service.approve(SESSION_ID);

        assertEquals(1L, session.getReviewedBy());
        assertNotNull(session.getReviewedAt());
        assertEquals("APPROVED", session.getStatus());
    }

    @Test
    void approveFailsOnDoubleApprove() {
        asStaff(BRANCH_ID);
        InventoryCountSessionModel session = pendingSession(SESSION_ID, BRANCH_ID);
        when(sessionRepository.findById(SESSION_ID)).thenReturn(Optional.of(session));
        when(itemRepository.findBySessionId(SESSION_ID)).thenReturn(List.of());
        when(sessionRepository.save(session)).thenReturn(session);
        when(userRepository.findAllById(any())).thenReturn(List.of(staff(1L, BRANCH_ID)));
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID)));

        service.approve(SESSION_ID);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.approve(SESSION_ID));

        assertEquals("Only pending sessions can be approved.", error.getMessage());
    }

    private void asStaff(Long branchId) {
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(staff(1L, branchId));
    }

    private static UserModel staff(Long id, Long branchId) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setBranchId(branchId);
        user.setFullName("Inventory Staff");
        return user;
    }

    private static BranchModel branch(Long id) {
        BranchModel branch = new BranchModel();
        branch.setId(id);
        branch.setName("District 1");
        return branch;
    }

    private static ProductModel product(Integer id, String code, String name) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setCode(code);
        product.setName(name);
        product.setUnit("bottle");
        return product;
    }

    private static BranchInventoryModel stockRow(Integer productId, int qty) {
        BranchInventoryModel row = new BranchInventoryModel();
        row.setBranchId(BRANCH_ID);
        row.setProductId(productId);
        row.setCurrentStock(qty);
        return row;
    }

    private static InventoryCountSessionModel pendingSession(Long id, Long branchId) {
        InventoryCountSessionModel session = new InventoryCountSessionModel();
        session.setId(id);
        session.setBranchId(branchId);
        session.setCountDate(LocalDate.now());
        session.setCountedBy(1L);
        session.setStatus("PENDING_APPROVAL");
        session.setTotalProducts(1);
        return session;
    }

    private static InventoryCountItemModel countItem(Long sessionId, Integer productId, int systemQty, int countedQty) {
        InventoryCountItemModel item = new InventoryCountItemModel();
        item.setSessionId(sessionId);
        item.setProductId(productId);
        item.setSystemQty(systemQty);
        item.setCountedQty(countedQty);
        item.setVariance(countedQty - systemQty);
        return item;
    }

    private static SubmitInventoryCountRequest submitRequest(Integer productId, int countedQty, String note) {
        SubmitInventoryCountRequest request = new SubmitInventoryCountRequest();
        request.setNote(note);
        SubmitInventoryCountRequest.Item item = new SubmitInventoryCountRequest.Item();
        item.setProductId(productId);
        item.setCountedQty(countedQty);
        request.setItems(List.of(item));
        return request;
    }
}
