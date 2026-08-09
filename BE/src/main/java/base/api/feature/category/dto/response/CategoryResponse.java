package base.api.feature.category.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CategoryResponse {

    private Integer id;
    private String name;
    private String description;
    private Integer parentId;
    private String parentName;
    private Boolean active;
    private Boolean shortDate;
}
