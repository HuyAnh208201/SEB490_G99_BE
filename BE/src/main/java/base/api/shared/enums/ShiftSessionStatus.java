package base.api.shared.enums;

/** Operational status of an employee shift session (open / close workflow). */
public enum ShiftSessionStatus {
    SCHEDULED,
    OPEN,
    PENDING_HANDOVER,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    CLOSED
}
