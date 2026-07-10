package base.api.shared.enums;

/**
 * Trạng thái đơn đặt hàng nhà cung cấp (purchase order) của KHO TỔNG.
 * ORDERED   = đã đặt nhà cung cấp
 * RECEIVED  = đã nhập kho tổng (tồn kho tổng tăng)
 * CANCELLED = đã huỷ
 */
public enum PurchaseOrderStatus {
    ORDERED,
    RECEIVED,
    CANCELLED;

    public static PurchaseOrderStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return PurchaseOrderStatus.valueOf(value.trim().replace(" ", "_").toUpperCase());
    }

    public boolean isReceivable() {
        return this == ORDERED;
    }

    public boolean isCancellable() {
        return this == ORDERED;
    }
}
