package base.api.feature.system.dto.request;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class UpdateShortDateCategoriesRequest {

    /** Category IDs that should be marked short_date=true; all others become false. */
    private List<Integer> categoryIds = new ArrayList<>();
}
