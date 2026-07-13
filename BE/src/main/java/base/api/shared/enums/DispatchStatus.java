package base.api.shared.enums;

/**
 * Trạng thái lô vận chuyển (dispatch order) từ kho tổng về chi nhánh.
 * PREPARING  = đang chuẩn bị / chờ vận chuyển
 * DELIVERING = đang vận chuyển
 * RECEIVED   = đã giao / chi nhánh đã nhận
 */
public enum DispatchStatus {
    PREPARING,
    DELIVERING,
    RECEIVED;

    public static DispatchStatus fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return DispatchStatus.valueOf(value.trim().replace(" ", "_").toUpperCase());
    }

    public DispatchStatus next() {
        return switch (this) {
            case PREPARING -> DELIVERING;
            case DELIVERING -> RECEIVED;
            case RECEIVED -> RECEIVED;
        };
    }
}
