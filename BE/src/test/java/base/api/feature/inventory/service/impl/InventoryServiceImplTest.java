package base.api.feature.inventory.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.inventory.dto.response.BranchInventoryItemResponse;
import base.api.feature.inventory.dto.response.WarehouseInventoryItemResponse;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.BranchInventoryModel;
import base.api.shared.entity.BranchModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InventoryServiceImpl} reorder-point updates and role/branch access.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    private static final Long BRANCH_ID = 10L;

    @Mock
    private WarehouseInventoryRepository warehouseInventoryRepository;

    @Mock
    private BranchInventoryRepository branchInventoryRepository;

    @Mock
    private IProductRepository productRepository;

    @Mock
    private IBranchRepository branchRepository;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ProductPackagingService productPackagingService;

    @InjectMocks
    private InventoryServiceImpl service;

    @Test
    void updateBranchReorderPointCreatesRowWhenMissing() {
        asAdmin();
        BranchModel branch = branch(BRANCH_ID, "District 1");
        ProductModel product = product(5, "P-001", "Cola");
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(productRepository.findByIdWithCategory(5)).thenReturn(Optional.of(product));
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, 5)).thenReturn(Optional.empty());
        when(branchInventoryRepository.save(any(BranchInventoryModel.class))).thenAnswer(inv -> {
            BranchInventoryModel row = inv.getArgument(0);
            row.setId(100L);
            return row;
        });
        when(productPackagingService.getTopPackaging(product)).thenReturn(null);
        when(productPackagingService.conversionQtyOf(null)).thenReturn(1);

        BranchInventoryItemResponse response = service.updateBranchReorderPoint(BRANCH_ID, 5, 12);

        assertEquals(12, response.getReorderPoint());
        assertEquals(0, response.getQuantity());
        assertTrue(response.getLowStock());
        ArgumentCaptor<BranchInventoryModel> captor = ArgumentCaptor.forClass(BranchInventoryModel.class);
        verify(branchInventoryRepository).save(captor.capture());
        assertEquals(BRANCH_ID, captor.getValue().getBranchId());
        assertEquals(5, captor.getValue().getProductId());
        assertEquals(12, captor.getValue().getReorderPoint());
    }

    @Test
    void updateBranchReorderPointUpdatesExistingRow() {
        asBranchManager(BRANCH_ID);
        BranchModel branch = branch(BRANCH_ID, "District 1");
        ProductModel product = product(5, "P-001", "Cola");
        BranchInventoryModel existing = new BranchInventoryModel();
        existing.setId(7L);
        existing.setBranchId(BRANCH_ID);
        existing.setProductId(5);
        existing.setCurrentStock(3);
        existing.setReorderPoint(1);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch));
        when(productRepository.findByIdWithCategory(5)).thenReturn(Optional.of(product));
        when(branchInventoryRepository.findByBranchIdAndProductId(BRANCH_ID, 5)).thenReturn(Optional.of(existing));
        when(branchInventoryRepository.save(existing)).thenReturn(existing);
        when(productPackagingService.getTopPackaging(product)).thenReturn(null);
        when(productPackagingService.conversionQtyOf(null)).thenReturn(1);

        BranchInventoryItemResponse response = service.updateBranchReorderPoint(BRANCH_ID, 5, 2);

        assertEquals(2, response.getReorderPoint());
        assertFalse(response.getLowStock());
        assertEquals(3, response.getQuantity());
    }

    @Test
    void updateBranchReorderPointRejectsNullProductId() {
        asAdmin();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, null, 5));

        assertEquals("Product id is required.", error.getMessage());
        verify(branchInventoryRepository, never()).save(any());
    }

    @Test
    void updateBranchReorderPointRejectsNegativeReorderPoint() {
        asAdmin();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, 5, -1));

        assertEquals("Reorder point must be zero or greater.", error.getMessage());
    }

    @Test
    void updateBranchReorderPointRejectsNullReorderPoint() {
        asAdmin();

        BadRequestException error = assertThrows(
                BadRequestException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, 5, null));

        assertEquals("Reorder point must be zero or greater.", error.getMessage());
    }

    @Test
    void updateBranchReorderPointForbiddenForOtherBranchManager() {
        asBranchManager(99L);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, 5, 3));

        assertEquals("Only the branch manager can update reorder points for this branch.", error.getMessage());
    }

    @Test
    void updateBranchReorderPointForbiddenForInventoryStaff() {
        UserModel staff = user(3L, BRANCH_ID);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(staff);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.INVENTORY_STAFF);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, 5, 3));

        assertEquals("Only the branch manager can update reorder points for this branch.", error.getMessage());
    }

    @Test
    void updateBranchReorderPointThrowsWhenBranchMissing() {
        asAdmin();
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, 5, 3));

        assertEquals("Branch not found.", error.getMessage());
    }

    @Test
    void updateBranchReorderPointThrowsWhenProductMissing() {
        asAdmin();
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID, "District 1")));
        when(productRepository.findByIdWithCategory(5)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.updateBranchReorderPoint(BRANCH_ID, 5, 3));

        assertEquals("Product not found.", error.getMessage());
    }

    @Test
    void getWarehouseInventoryForbiddenForBranchManager() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.getWarehouseInventory());

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void getWarehouseInventoryAllowedForWarehouseManager() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.WAREHOUSE_MANAGER);
        WarehouseInventoryModel row = new WarehouseInventoryModel();
        row.setId(1L);
        row.setProductId(5);
        row.setQuantity(2);
        row.setReorderPoint(5);
        when(warehouseInventoryRepository.findAll()).thenReturn(List.of(row));
        when(productRepository.findByIdInWithCategory(Set.of(5))).thenReturn(List.of(product(5, "P-001", "Cola")));

        List<WarehouseInventoryItemResponse> rows = service.getWarehouseInventory();

        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isLowStock());
    }

    @Test
    void getBranchInventoryForbiddenForOtherBranchStaff() {
        UserModel staff = user(3L, 99L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(staff);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.getBranchInventory(BRANCH_ID));

        assertEquals("Access denied.", error.getMessage());
    }

    @Test
    void getBranchInventoryAllowedForSameBranchManager() {
        asBranchManager(BRANCH_ID);
        when(branchRepository.findById(BRANCH_ID)).thenReturn(Optional.of(branch(BRANCH_ID, "District 1")));
        when(branchInventoryRepository.findByBranchId(BRANCH_ID)).thenReturn(List.of());

        List<BranchInventoryItemResponse> rows = service.getBranchInventory(BRANCH_ID);

        assertTrue(rows.isEmpty());
    }

    private void asAdmin() {
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user(1L, null));
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
    }

    private void asBranchManager(Long branchId) {
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(user(2L, branchId));
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
    }

    private static UserModel user(Long id, Long branchId) {
        UserModel user = new UserModel();
        user.setId(id);
        user.setBranchId(branchId);
        return user;
    }

    private static BranchModel branch(Long id, String name) {
        BranchModel branch = new BranchModel();
        branch.setId(id);
        branch.setName(name);
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
}
