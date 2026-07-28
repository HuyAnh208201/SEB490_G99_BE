package base.api.shared.enums;

public enum PurchaseRequestStatus {
    DRAFT,
    PENDING,
    APPROVED,
    /** Kho tổng đã duyệt nhưng tồn kho tổng không đủ → chờ đặt nhà cung cấp bổ sung. */
    AWAITING_STOCK,
    /** Đã gom vào lô vận chuyển, chờ xuất kho (dispatch order = PREPARING). */
    DISPATCHING,
    /** Đang vận chuyển về chi nhánh (dispatch order = DELIVERING). */
    IN_TRANSIT,
    REJECTED,
    RECEIVED,
    CANCELLED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isCancellable() {
        return this == DRAFT;
    }

    public boolean isApprovable() {
        return this == PENDING;
    }

    public boolean isReceivable() {
        // Branch receive goes through dispatch flow (IN_TRANSIT) via branch-receiving API.
        return false;
    }

    /** Yêu cầu đã duyệt, đủ tồn kho tổng → có thể gom đơn vận chuyển. */
    public boolean isDispatchable() {
        return this == APPROVED;
    }

    public boolean isWarehouseVisible() {
        return this == PENDING
                || this == APPROVED
                || this == AWAITING_STOCK
                || this == DISPATCHING
                || this == IN_TRANSIT
                || this == RECEIVED;
    }
}
