package base.api.feature.inventorycount.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Dữ liệu khởi tạo màn Inventory Count: mã phiên gợi ý, ngày, chi nhánh và danh sách sản phẩm.
 */
@Getter
@Setter
public class InventoryCountSheetResponse {
    private String sessionCode;
    private LocalDate countDate;
    private Long branchId;
    private String branchName;
    private List<InventoryCountProductResponse> products = new ArrayList<>();
    /** 1-based page when sheet is loaded with pagination. */
    private Integer page;
    private Integer totalPages;
    private Long totalElements;
}
