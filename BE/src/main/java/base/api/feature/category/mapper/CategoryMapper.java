package base.api.feature.category.mapper;

import base.api.feature.category.dto.response.CategoryResponse;
import base.api.shared.entity.CategoryModel;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(CategoryModel category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setActive(category.getActive() == null || Boolean.TRUE.equals(category.getActive()));
        response.setShortDate(Boolean.TRUE.equals(category.getShortDate()));

        if (category.getParentCategory() != null) {
            response.setParentId(category.getParentCategory().getId());
            response.setParentName(category.getParentCategory().getName());
        }

        return response;
    }
}
