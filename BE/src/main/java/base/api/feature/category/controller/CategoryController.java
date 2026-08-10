package base.api.feature.category.controller;

import base.api.feature.category.dto.request.CreateCategoryRequest;
import base.api.feature.category.dto.request.UpdateCategoryRequest;
import base.api.feature.category.dto.response.CategoryResponse;
import base.api.feature.category.service.ICategoryService;
import base.api.shared.base.BaseAPIController;
import base.api.shared.dto.TFUResponse;
import base.api.shared.dto.PageRequestDTO;
import base.api.shared.dto.PageResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Product Categories", description = "Backend CRUD cho product categories")
public class CategoryController extends BaseAPIController {

    @Autowired
    private ICategoryService categoryService;

    @Operation(summary = "Create category")
    @PreAuthorize("@permissionChecker.has('CATEGORY_MANAGEMENT')")
    @PostMapping
    public ResponseEntity<TFUResponse<CategoryResponse>> create(@Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse data = categoryService.create(request);
        TFUResponse<CategoryResponse> body = new TFUResponse<>(
                true, data, "Category created successfully.", HttpStatus.CREATED.value(), null
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @Operation(summary = "Get categories (active only by default)")
    @GetMapping
    public ResponseEntity<TFUResponse<List<CategoryResponse>>> getAll(
            @RequestParam(defaultValue = "false") boolean includeInactive) {
        return success(categoryService.getAll(includeInactive));
    }

    @Operation(summary = "Search and paginate categories")
    @PreAuthorize("@permissionChecker.has('CATEGORY_MANAGEMENT')")
    @GetMapping("/page")
    public ResponseEntity<TFUResponse<PageResponseDTO<CategoryResponse>>> getPage(
            @ModelAttribute PageRequestDTO pageRequest,
            @RequestParam(defaultValue = "true") boolean includeInactive) {
        return successPage(categoryService.getPage(pageRequest, includeInactive));
    }

    @Operation(summary = "Get category detail")
    @GetMapping("/{id}")
    public ResponseEntity<TFUResponse<CategoryResponse>> getById(@PathVariable Integer id) {
        return success(categoryService.getById(id));
    }

    @Operation(summary = "Update category")
    @PreAuthorize("@permissionChecker.has('CATEGORY_MANAGEMENT')")
    @PutMapping("/{id}")
    public ResponseEntity<TFUResponse<CategoryResponse>> update(
            @PathVariable Integer id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return success(categoryService.update(id, request), "Category updated successfully.");
    }

    @Operation(summary = "Deactivate category (soft hide — delete is not allowed)")
    @PreAuthorize("@permissionChecker.has('CATEGORY_MANAGEMENT')")
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<TFUResponse<CategoryResponse>> deactivate(@PathVariable Integer id) {
        return success(categoryService.deactivate(id), "Category deactivated.");
    }

    @Operation(summary = "Activate category")
    @PreAuthorize("@permissionChecker.has('CATEGORY_MANAGEMENT')")
    @PatchMapping("/{id}/activate")
    public ResponseEntity<TFUResponse<CategoryResponse>> activate(@PathVariable Integer id) {
        return success(categoryService.activate(id), "Category activated.");
    }

    @Operation(summary = "Delete category — disabled; use deactivate")
    @PreAuthorize("@permissionChecker.has('CATEGORY_MANAGEMENT')")
    @DeleteMapping("/{id}")
    public ResponseEntity<TFUResponse<Void>> delete(@PathVariable Integer id) {
        categoryService.delete(id);
        return success(null);
    }
}
