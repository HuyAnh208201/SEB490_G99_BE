package base.api.feature.category.service.impl;

import base.api.feature.category.dto.request.CreateCategoryRequest;
import base.api.feature.category.dto.request.UpdateCategoryRequest;
import base.api.feature.category.dto.response.CategoryResponse;
import base.api.feature.category.mapper.CategoryMapper;
import base.api.feature.category.repository.ICategoryRepository;
import base.api.feature.product.repository.IProductRepository;
import base.api.feature.category.service.ICategoryService;
import base.api.shared.entity.CategoryModel;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.exception.BadRequestException;
import base.api.shared.exception.ConflictException;
import base.api.shared.exception.NotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CategoryServiceImpl implements ICategoryService {

    @Autowired
    private ICategoryRepository categoryRepository;

    @Autowired
    private IProductRepository productRepository;

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String normalizedName = normalizeName(request.getName());
        validateDuplicateName(normalizedName, null);

        CategoryModel category = new CategoryModel();
        category.setName(normalizedName);
        category.setDescription(normalizeNullableText(request.getDescription()));
        category.setParentCategory(resolveParentCategory(request.getParentId(), null));

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public CategoryResponse update(Integer id, UpdateCategoryRequest request) {
        CategoryModel category = findCategoryOrThrow(id);
        String normalizedName = normalizeName(request.getName());

        validateDuplicateName(normalizedName, id);

        category.setName(normalizedName);
        category.setDescription(normalizeNullableText(request.getDescription()));
        category.setParentCategory(resolveParentCategory(request.getParentId(), id));

        return categoryMapper.toResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        CategoryModel category = findCategoryOrThrow(id);

        if (productRepository.existsByCategory_Id(id)) {
            throw new ConflictException("Category is being used by existing products.");
        }

        if (categoryRepository.existsByParentCategoryId(id)) {
            throw new ConflictException("Cannot delete category because it has child categories.");
        }

        categoryRepository.delete(category);
    }

    @Override
    public CategoryResponse getById(Integer id) {
        return categoryMapper.toResponse(findCategoryOrThrow(id));
    }

    @Override
    public List<CategoryResponse> getAll() {
        return categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @Override
    public Page<CategoryResponse> getPage(PageRequestDTO pageRequest) {
        PageRequestDTO query = pageRequest == null ? new PageRequestDTO() : pageRequest;
        Specification<CategoryModel> specification = (root, ignored, cb) -> cb.conjunction();
        String search = query.normalizedSearch();
        if (search != null) {
            String pattern = "%" + search.toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, ignored, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("description")), pattern)
            ));
        }
        return categoryRepository.findAll(
                        specification,
                        query.toPageable("id", Sort.Direction.ASC, Set.of("id", "name")))
                .map(categoryMapper::toResponse);
    }

    private CategoryModel findCategoryOrThrow(Integer id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Category not found."));
    }

    private void validateDuplicateName(String name, Integer currentId) {
        boolean exists = currentId == null
                ? categoryRepository.existsByNameIgnoreCase(name)
                : categoryRepository.existsByNameIgnoreCaseAndIdNot(name, currentId);

        if (exists) {
            throw new ConflictException("Category name already exists.");
        }
    }

    private CategoryModel resolveParentCategory(Integer parentId, Integer currentCategoryId) {
        if (parentId == null) {
            return null;
        }

        if (currentCategoryId != null && currentCategoryId.equals(parentId)) {
            throw new BadRequestException("Category cannot be its own parent.");
        }

        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new BadRequestException("Parent category not found."));
    }

    private String normalizeName(String name) {
        String normalized = normalizeWhitespace(name);
        if (normalized == null || normalized.isBlank()) {
            throw new BadRequestException("Category name is required.");
        }
        return normalized;
    }

    private String normalizeNullableText(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }
        return normalized;
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}
