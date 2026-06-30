package base.api.feature.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateCategoryRequest {

    @NotBlank(message = "Category name is required.")
    @Size(max = 255, message = "Category name must not exceed 255 characters.")
    private String name;

    private Integer parentId;

    private String description;
}
