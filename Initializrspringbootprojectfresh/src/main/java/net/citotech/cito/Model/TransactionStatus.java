package net.citotech.cito.Model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Explicit status enum + state machine for {@code merchant_transactions_log.status} (audit B2).
 * The legacy money path still stores/reads this column as a free string, so this enum is additive
 * - it gives new/updated code (ledger dual-write, callback handling) a type-safe, validated
 * status model without requiring every existing literal-string call site to be rewritten at once.
 */
public enum TransactionStatus {
    PENDING,
    SUCCESSFUL,
    FAILED,
    UNDETERMINED;

    private static final Set<TransactionStatus> TERMINAL = EnumSet.of(SUCCESSFUL, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    /** A terminal status (SUCCESSFUL/FAILED) can never transition again - it is final. */
    public boolean canTransitionTo(TransactionStatus next) {
        if (next == null) {
            return false;
        }
        return !this.isTerminal();
    }

    public static TransactionStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
