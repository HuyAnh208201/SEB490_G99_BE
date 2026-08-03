package base.api.feature.inventory.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateBranchReorderPointRequest {

    @NotNull
    @Min(0)
    private Integer reorderPoint;
}
