package base.api.feature.category.service.impl;

import base.api.feature.category.dto.request.CreateCategoryRequest;
import base.api.feature.category.dto.request.UpdateCategoryRequest;
import base.api.feature.category.dto.response.CategoryResponse;
import base.api.feature.category.mapper.CategoryMapper;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.shared.entity.CategoryModel;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CategoryServiceImpl} create / update / delete validation paths.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private ICategoryRepository categoryRepository;

    @Mock
    private IProductRepository productRepository;

    @Mock
    private CategoryMapper categoryMapper;

    @InjectMocks
    private CategoryServiceImpl service;

    @Test
    void createSavesNormalizedNameWithoutParent() {
        CreateCategoryRequest request = createRequest("  Soft  Drinks  ", null, "  fizzy  ");
        when(categoryRepository.existsByNameIgnoreCase("Soft Drinks")).thenReturn(false);
        when(categoryRepository.save(any(CategoryModel.class))).thenAnswer(inv -> {
            CategoryModel saved = inv.getArgument(0);
            saved.setId(1);
            return saved;
        });
        CategoryResponse mapped = new CategoryResponse();
        mapped.setId(1);
        mapped.setName("Soft Drinks");
        when(categoryMapper.toResponse(any(CategoryModel.class))).thenReturn(mapped);

        CategoryResponse response = service.create(request);

        assertEquals(1, response.getId());
        ArgumentCaptor<CategoryModel> captor = ArgumentCaptor.forClass(CategoryModel.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals("Soft Drinks", captor.getValue().getName());
        assertEquals("fizzy", captor.getValue().getDescription());
        assertNull(captor.getValue().getParentCategory());
    }

    @Test
    void createAttachesExistingParent() {
        CreateCategoryRequest request = createRequest("Cola", 5, null);
        CategoryModel parent = category(5, "Beverages");
        when(categoryRepository.existsByNameIgnoreCase("Cola")).thenReturn(false);
        when(categoryRepository.findById(5)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(CategoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        when(categoryMapper.toResponse(any(CategoryModel.class))).thenReturn(new CategoryResponse());

        service.create(request);

        ArgumentCaptor<CategoryModel> captor = ArgumentCaptor.forClass(CategoryModel.class);
        verify(categoryRepository).save(captor.capture());
        assertEquals(parent, captor.getValue().getParentCategory());
    }

    @Test
    void createRejectsBlankName() {
        CreateCategoryRequest request = createRequest("   ", null, null);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Category name is required.", error.getMessage());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateName() {
        CreateCategoryRequest request = createRequest("Snacks", null, null);
        when(categoryRepository.existsByNameIgnoreCase("Snacks")).thenReturn(true);

        ConflictException error = assertThrows(ConflictException.class, () -> service.create(request));

        assertEquals("Category name already exists.", error.getMessage());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void createRejectsMissingParent() {
        CreateCategoryRequest request = createRequest("Cola", 99, null);
        when(categoryRepository.existsByNameIgnoreCase("Cola")).thenReturn(false);
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.create(request));

        assertEquals("Parent category not found.", error.getMessage());
    }

    @Test
    void updateRejectsSelfAsParent() {
        CategoryModel existing = category(3, "Beverages");
        when(categoryRepository.findById(3)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Beverages", 3)).thenReturn(false);
        UpdateCategoryRequest request = updateRequest("Beverages", 3, null);

        BadRequestException error = assertThrows(BadRequestException.class, () -> service.update(3, request));

        assertEquals("Category cannot be its own parent.", error.getMessage());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void updateRejectsDuplicateNameOnOtherCategory() {
        when(categoryRepository.findById(2)).thenReturn(Optional.of(category(2, "Old")));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Snacks", 2)).thenReturn(true);
        UpdateCategoryRequest request = updateRequest("Snacks", null, null);

        ConflictException error = assertThrows(ConflictException.class, () -> service.update(2, request));

        assertEquals("Category name already exists.", error.getMessage());
    }

    @Test
    void updateSucceedsForExistingCategory() {
        CategoryModel existing = category(2, "Old");
        when(categoryRepository.findById(2)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("New Name", 2)).thenReturn(false);
        when(categoryRepository.save(any(CategoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse mapped = new CategoryResponse();
        mapped.setName("New Name");
        when(categoryMapper.toResponse(any(CategoryModel.class))).thenReturn(mapped);

        CategoryResponse response = service.update(2, updateRequest("New Name", null, "desc"));

        assertEquals("New Name", response.getName());
        verify(categoryRepository).save(existing);
        assertEquals("New Name", existing.getName());
        assertEquals("desc", existing.getDescription());
    }

    @Test
    void deleteAlwaysRejectsInFavorOfDeactivate() {
        ConflictException error = assertThrows(ConflictException.class, () -> service.delete(1));

        assertTrue(error.getMessage().contains("Deactivate"));
        verify(categoryRepository, never()).delete(any(CategoryModel.class));
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void deactivateSetsActiveFalse() {
        CategoryModel category = category(1, "Snacks");
        category.setActive(true);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByParentCategoryId(1)).thenReturn(false);
        when(categoryRepository.save(any(CategoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse mapped = new CategoryResponse();
        mapped.setActive(false);
        when(categoryMapper.toResponse(any(CategoryModel.class))).thenReturn(mapped);

        CategoryResponse response = service.deactivate(1);

        assertEquals(false, response.getActive());
        assertEquals(false, category.getActive());
        verify(categoryRepository).save(category);
    }

    @Test
    void activateSetsActiveTrue() {
        CategoryModel category = category(1, "Snacks");
        category.setActive(false);
        when(categoryRepository.findById(1)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(CategoryModel.class))).thenAnswer(inv -> inv.getArgument(0));
        CategoryResponse mapped = new CategoryResponse();
        mapped.setActive(true);
        when(categoryMapper.toResponse(any(CategoryModel.class))).thenReturn(mapped);

        CategoryResponse response = service.activate(1);

        assertEquals(true, response.getActive());
        assertEquals(true, category.getActive());
    }

    private static CreateCategoryRequest createRequest(String name, Integer parentId, String description) {
        CreateCategoryRequest request = new CreateCategoryRequest();
        request.setName(name);
        request.setParentId(parentId);
        request.setDescription(description);
        return request;
    }

    private static UpdateCategoryRequest updateRequest(String name, Integer parentId, String description) {
        UpdateCategoryRequest request = new UpdateCategoryRequest();
        request.setName(name);
        request.setParentId(parentId);
        request.setDescription(description);
        return request;
    }

    private static CategoryModel category(Integer id, String name) {
        CategoryModel model = new CategoryModel();
        model.setId(id);
        model.setName(name);
        return model;
    }
}
