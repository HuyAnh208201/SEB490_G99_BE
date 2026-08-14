package base.api.feature.purchaseorder.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Đơn đặt hàng nhà cung cấp — dùng cho cả danh sách và chi tiết.
 */
@Getter
@Setter
public class PurchaseOrderResponse {
    private Long id;
    private String orderNumber;
    private Integer supplierId;
    private String supplierName;
    private String status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime receivedAt;
    private LocalDate supplierDeliveryDate;
    private String deliveredByName;
    private String deliveredByPhone;
    private String supplierDocumentNumber;
    private Long receivedBy;
    private String receivedByName;
    private String receivedByPhone;
    private Integer itemCount;
    private Integer totalQuantity;
    private List<ItemLine> items = new ArrayList<>();

    @Getter
    @Setter
    public static class ItemLine {
        private Integer productId;
        private String productCode;
        private String productName;
        private String unit;
        private String importUnit;
        private Integer conversionQty;
        private Integer quantity;
        private Integer quantityBase;
        private BigDecimal unitPrice;
    }
}
