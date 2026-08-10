package base.api.feature.category.service;

import base.api.feature.category.dto.request.CreateCategoryRequest;
import base.api.feature.category.dto.request.UpdateCategoryRequest;
import base.api.feature.category.dto.response.CategoryResponse;
import base.api.shared.dto.PageRequestDTO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface ICategoryService {

    CategoryResponse create(CreateCategoryRequest request);

    CategoryResponse update(Integer id, UpdateCategoryRequest request);

    /** @deprecated Use deactivate instead — hard delete is no longer supported. */
    @Deprecated
    void delete(Integer id);

    CategoryResponse deactivate(Integer id);

    CategoryResponse activate(Integer id);

    CategoryResponse getById(Integer id);

    List<CategoryResponse> getAll(boolean includeInactive);

    Page<CategoryResponse> getPage(PageRequestDTO pageRequest, boolean includeInactive);
}
