package base.api.feature.product.service.impl;

import base.api.feature.branch.repository.IBranchRepository;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.product.dto.request.CreateProductRequest;
import base.api.feature.product.dto.request.UpdateProductRequest;
import base.api.feature.product.dto.response.ProductResponse;
import base.api.feature.product.mapper.ProductMapper;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.product.service.ProductPackagingService;
import base.api.feature.purchaserequest.repository.BranchInventoryRepository;
import base.api.feature.purchaserequest.repository.WarehouseInventoryRepository;
import base.api.shared.entity.CategoryModel;
import base.api.shared.entity.ProductModel;
import base.api.shared.entity.UserModel;
import base.api.shared.entity.WarehouseInventoryModel;
import base.api.shared.enums.ProductScope;
import base.api.shared.enums.UserRole;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProductServiceImpl} create / update / delete mutation paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServiceImplTest {

    @Mock private IProductRepository productRepository;
    @Mock private ICategoryRepository categoryRepository;
    @Mock private ProductMapper productMapper;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private BranchInventoryRepository branchInventoryRepository;
    @Mock private WarehouseInventoryRepository warehouseInventoryRepository;
    @Mock private IBranchRepository branchRepository;
    @Mock private ProductPackagingService productPackagingService;

    @InjectMocks
    private ProductServiceImpl service;

    @Test
    void createSavesGlobalProductAsAdmin() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", "8901234567890");
        request.setRefundable(false);
        when(productRepository.existsByCode("P001")).thenReturn(false);
        when(productRepository.existsByBarcode("8901234567890")).thenReturn(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1)));
        when(productRepository.save(any(ProductModel.class))).thenAnswer(inv -> {
            ProductModel saved = inv.getArgument(0);
            saved.setId(10);
            return saved;
        });
        when(warehouseInventoryRepository.findByProductId(10)).thenReturn(Optional.empty());
        when(warehouseInventoryRepository.save(any(WarehouseInventoryModel.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        ProductResponse mapped = new ProductResponse();
        mapped.setId(10);
        when(productMapper.toResponse(any(ProductModel.class))).thenReturn(mapped);

        ProductResponse response = service.create(request);

        assertEquals(10, response.getId());
        ArgumentCaptor<ProductModel> captor = ArgumentCaptor.forClass(ProductModel.class);
        verify(productRepository).save(captor.capture());
        assertEquals("P001", captor.getValue().getCode());
        assertEquals(ProductScope.GLOBAL.getValue(), captor.getValue().getScope());
        assertNull(captor.getValue().getBranchId());
        assertEquals("active", captor.getValue().getStatus());
        assertEquals(Boolean.FALSE, captor.getValue().getRefundable());
        verify(productPackagingService).ensureDefaultPackagings(any(ProductModel.class));
    }

    @Test
    void createRejectsBranchManager() {
        asBranchManager(5L);
        CreateProductRequest request = createRequest("LOCAL-1", "Store Cola", "can", null);
        when(productRepository.existsByCode("LOCAL-1")).thenReturn(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1)));

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.create(request));

        assertTrue(error.getMessage().contains("view-only product access"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsBlankCode() {
        asAdmin();
        CreateProductRequest request = createRequest("  ", "Cola", "bottle", null);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Product code is required.", error.getMessage());
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCode() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", null);
        when(productRepository.existsByCode("P001")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(request));

        assertEquals("Product code already exists.", error.getMessage());
    }

    @Test
    void createRejectsDuplicateBarcode() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", "8901234567890");
        when(productRepository.existsByCode("P001")).thenReturn(false);
        when(productRepository.existsByBarcode("8901234567890")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(request));

        assertEquals("Barcode already exists.", error.getMessage());
    }

    @Test
    void createRejectsNegativeImportPrice() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", null);
        request.setReferenceImportPrice(new BigDecimal("-1"));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Reference import price must be greater than or equal to 0.", error.getMessage());
    }

    @Test
    void createRejectsSalePriceBelowImportPrice() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", null);
        request.setReferenceImportPrice(new BigDecimal("20"));
        request.setDefaultSalePrice(new BigDecimal("10"));

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Default sale price must not be smaller than reference import price.", error.getMessage());
    }

    @Test
    void createRejectsMissingCategory() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", null);
        when(productRepository.existsByCode("P001")).thenReturn(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Category not found.", error.getMessage());
    }

    @Test
    void createRejectsWarehouseManager() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.WAREHOUSE_MANAGER);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.create(createRequest("P001", "Cola", "bottle", null)));

        assertEquals("Warehouse managers have read-only product access.", error.getMessage());
    }

    @Test
    void createRejectsBranchManagerWithoutBranch() {
        UserModel actor = new UserModel();
        actor.setId(2L);
        actor.setBranchId(null);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
        CreateProductRequest request = createRequest("LOCAL-1", "Store Cola", "can", null);
        when(productRepository.existsByCode("LOCAL-1")).thenReturn(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1)));

        ForbiddenException error = assertThrows(ForbiddenException.class, () -> service.create(request));

        assertTrue(error.getMessage().contains("view-only product access"));
    }

    @Test
    void updateSucceedsForExistingProduct() {
        asAdmin();
        ProductModel existing = product(10, ProductScope.GLOBAL.getValue(), null);
        when(productRepository.findByIdWithCategory(10)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category(1)));
        when(productRepository.save(any(ProductModel.class))).thenAnswer(inv -> inv.getArgument(0));
        ProductResponse mapped = new ProductResponse();
        mapped.setName("Updated Cola");
        when(productMapper.toResponse(any())).thenReturn(mapped);

        ProductResponse response = service.update(10, updateRequest("Updated Cola", "active"));

        assertEquals("Updated Cola", response.getName());
        assertEquals("Updated Cola", existing.getName());
        assertEquals("active", existing.getStatus());
        verify(productPackagingService).ensureDefaultPackagings(existing);
    }

    @Test
    void updateRejectsMissingProduct() {
        asAdmin();
        when(productRepository.findByIdWithCategory(99)).thenReturn(Optional.empty());

        NotFoundException error = assertThrows(
                NotFoundException.class, () -> service.update(99, updateRequest("Name", "active")));

        assertEquals("Product not found.", error.getMessage());
    }

    @Test
    void updateRejectsBranchManagerEditingGlobalProduct() {
        asBranchManager(5L);
        ProductModel global = product(10, ProductScope.GLOBAL.getValue(), null);
        when(productRepository.findByIdWithCategory(10)).thenReturn(Optional.of(global));

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.update(10, updateRequest("Name", "active")));

        assertTrue(error.getMessage().contains("view-only product access"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void createRejectsInventoryStaff() {
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.INVENTORY_STAFF);

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.create(createRequest("P001", "Cola", "bottle", null)));

        assertTrue(error.getMessage().contains("view-only product access"));
    }

    @Test
    void updateRejectsInventoryStaff() {
        asInventoryStaff(5L);
        ProductModel global = product(10, ProductScope.GLOBAL.getValue(), null);
        when(productRepository.findByIdWithCategory(10)).thenReturn(Optional.of(global));

        ForbiddenException error = assertThrows(
                ForbiddenException.class, () -> service.update(10, updateRequest("Name", "active")));

        assertTrue(error.getMessage().contains("view-only product access"));
        verify(productRepository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateBarcodeOnOtherProduct() {
        asAdmin();
        when(productRepository.findByIdWithCategory(10))
                .thenReturn(Optional.of(product(10, ProductScope.GLOBAL.getValue(), null)));
        UpdateProductRequest request = updateRequest("Cola", "active");
        request.setBarcode("8901234567890");
        when(productRepository.existsByBarcodeAndIdNot("8901234567890", 10)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.update(10, request));

        assertEquals("Barcode already exists.", error.getMessage());
    }

    @Test
    void deleteRemovesMutableProduct() {
        asAdmin();
        ProductModel existing = product(10, ProductScope.GLOBAL.getValue(), null);
        when(productRepository.findByIdWithCategory(10)).thenReturn(Optional.of(existing));
        when(branchInventoryRepository.existsByProductId(10)).thenReturn(false);
        when(warehouseInventoryRepository.existsByProductId(10)).thenReturn(false);

        service.delete(10);

        verify(productRepository).delete(existing);
    }

    @Test
    void deleteRejectsWhenBranchInventoryExists() {
        asAdmin();
        ProductModel existing = product(10, ProductScope.GLOBAL.getValue(), null);
        when(productRepository.findByIdWithCategory(10)).thenReturn(Optional.of(existing));
        when(branchInventoryRepository.existsByProductId(10)).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.delete(10));

        assertTrue(error.getMessage().contains("branch inventory"));
        verify(productRepository, never()).delete(any(ProductModel.class));
    }

    @Test
    void createRejectsDuplicateName() {
        asAdmin();
        CreateProductRequest request = createRequest("P001", "Cola", "bottle", null);
        when(productRepository.existsByCode("P001")).thenReturn(false);
        when(productRepository.existsByNameIgnoreCase("Cola")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(request));

        assertEquals("A product with this name already exists.", error.getMessage());
    }

    private void asAdmin() {
        UserModel actor = new UserModel();
        actor.setId(1L);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.ADMIN);
    }

    private void asBranchManager(Long branchId) {
        UserModel actor = new UserModel();
        actor.setId(2L);
        actor.setBranchId(branchId);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.BRANCH_MANAGER);
    }

    private void asInventoryStaff(Long branchId) {
        UserModel actor = new UserModel();
        actor.setId(3L);
        actor.setBranchId(branchId);
        when(currentUserProvider.getCurrentUserOrThrow()).thenReturn(actor);
        when(currentUserProvider.getCurrentUserRole()).thenReturn(UserRole.INVENTORY_STAFF);
    }

    private static CreateProductRequest createRequest(String code, String name, String unit, String barcode) {
        CreateProductRequest request = new CreateProductRequest();
        request.setCode(code);
        request.setName(name);
        request.setUnit(unit);
        request.setBarcode(barcode);
        request.setCategoryId(1);
        request.setReferenceImportPrice(new BigDecimal("10"));
        request.setDefaultSalePrice(new BigDecimal("15"));
        return request;
    }

    private static UpdateProductRequest updateRequest(String name, String status) {
        UpdateProductRequest request = new UpdateProductRequest();
        request.setName(name);
        request.setUnit("bottle");
        request.setCategoryId(1);
        request.setReferenceImportPrice(new BigDecimal("10"));
        request.setDefaultSalePrice(new BigDecimal("15"));
        request.setStatus(status);
        return request;
    }

    private static CategoryModel category(Integer id) {
        CategoryModel category = new CategoryModel();
        category.setId(id);
        category.setName("Beverages");
        return category;
    }

    private static ProductModel product(Integer id, String scope, Long branchId) {
        ProductModel product = new ProductModel();
        product.setId(id);
        product.setCode("P" + id);
        product.setName("Product " + id);
        product.setScope(scope);
        product.setBranchId(branchId);
        product.setStatus("active");
        return product;
    }
}
