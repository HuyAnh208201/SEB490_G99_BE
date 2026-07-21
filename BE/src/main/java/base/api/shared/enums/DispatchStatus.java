package base.api.shared.enums;

/**
 * Trạng thái lô vận chuyển (dispatch order) từ kho tổng về chi nhánh.
 * PREPARING  = chuẩn bị / chờ xuất kho
 * DELIVERING = đang vận chuyển
 * REDELIVERY = giao lại hàng (giao không thành công, cần giao lại)
 * RECEIVED   = đã giao — chỉ chuyển khi chi nhánh (inventory staff) xác nhận nhận hàng
 */
public enum DispatchStatus {
    PREPARING,
    DELIVERING,
    REDELIVERY,
    RECEIVED;

    public static DispatchStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DispatchStatus.valueOf(value.trim().replace(" ", "_").toUpperCase());
    }

    /** Trạng thái kho tổng được chọn thủ công trên màn Dispatch Orders. */
    public boolean isWarehouseSelectable() {
        return this == PREPARING || this == DELIVERING || this == REDELIVERY;
    }

    /** Chỉ inventory staff xác nhận nhận hàng mới được chuyển sang RECEIVED. */
    public boolean isBranchReceiptOnly() {
        return this == RECEIVED;
    }
}
