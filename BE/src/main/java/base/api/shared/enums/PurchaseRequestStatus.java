package base.api.shared.enums;

public enum PurchaseRequestStatus {
    DRAFT,
    PENDING,
    APPROVED,
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
        return this == APPROVED;
    }

    public boolean isWarehouseVisible() {
        return this == PENDING || this == APPROVED || this == RECEIVED;
    }
}
