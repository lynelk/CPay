package net.citotech.cito.refund;

import java.util.EnumSet;
import java.util.Set;

/** Explicit refund lifecycle state machine. */
public enum RefundStatus {
    REQUESTED,
    PENDING_APPROVAL,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED;

    private static final Set<RefundStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, REJECTED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean canTransitionTo(RefundStatus next) {
        if (next == null || this.isTerminal()) {
            return false;
        }
        return switch (this) {
            case REQUESTED ->
                    next == PENDING_APPROVAL
                            || next == PROCESSING
                            || next == REJECTED
                            || next == FAILED;
            case PENDING_APPROVAL -> next == PROCESSING || next == REJECTED || next == FAILED;
            case PROCESSING -> next == COMPLETED || next == FAILED;
            default -> false;
        };
    }
}