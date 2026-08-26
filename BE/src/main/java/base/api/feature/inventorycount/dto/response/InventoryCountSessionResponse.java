package base.api.feature.inventorycount.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phiên kiểm kê — dùng cho cả danh sách Count History và chi tiết.
 */
@Getter
@Setter
public class InventoryCountSessionResponse {
    private Long id;
    private String sessionCode;
    private LocalDate countDate;
    private Long branchId;
    private String branchName;
    private String countedByName;
    private String reviewedByName;
    private Integer totalProducts;
    private Integer varianceCount;
    private Boolean hasDiscrepancy;
    private String status;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private List<Item> items = new ArrayList<>();

    @Getter
    @Setter
    public static class Item {
        private Integer productId;
        private String productCode;
        private String productName;
        private String unit;
        private String category;
        private Integer systemQty;
        private Integer countedQty;
        private Integer variance;
        private String note;
    }
}
