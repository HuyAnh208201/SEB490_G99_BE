package base.api.feature.purchaserequest.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ConsolidatedBranchResponse {

    private Long branchId;
    private String branchName;
    private String branchAddress;
    private List<CategoryGroup> categories = new ArrayList<>();

    @Getter
    @Setter
    public static class CategoryGroup {
        private Integer categoryId;
        private String categoryName;
        private List<ConsolidatedItem> items = new ArrayList<>();
    }

    @Getter
    @Setter
    public static class ConsolidatedItem {
        private Integer productId;
        private String productCode;
        private String productName;
        private String unit;
        private Integer totalQuantity;
    }
}
