package base.api.shared.enums;

/** Operational status of an employee shift session (open / close workflow). */
public enum ShiftSessionStatus {
    SCHEDULED,
    OPEN,
    /** Legacy alias — treated like {@link #CLOSING} in workflow code. */
    PENDING_HANDOVER,
    CLOSING,
    /** Legacy terminal state — treat like {@link #COMPLETED}. */
    CLOSED,
    COMPLETED,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED;

    public boolean isTerminal() {
        return this == CLOSED
                || this == COMPLETED
                || this == APPROVED
                || this == REJECTED;
    }

    public boolean blocksBranchOpening() {
        return this == OPEN;
    }

    public boolean isActiveForCashier() {
        return this == OPEN || this == CLOSING || this == PENDING_HANDOVER;
    }
}
