package base.api.shared.enums;

public enum PurchaseRequestStatus {
    DRAFT,
    PENDING,
    PREPARING,
    SENT,
    CANCELLED;

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isCancellable() {
        return this == DRAFT;
    }

    public boolean isWarehouseVisible() {
        return this == PENDING || this == PREPARING || this == SENT;
    }
}
