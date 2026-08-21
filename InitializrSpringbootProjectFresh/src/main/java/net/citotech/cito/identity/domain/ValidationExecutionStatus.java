package net.citotech.cito.identity.domain;

import java.util.Locale;

/**
 * Lifecycle of a compute execution (ISO domain mapping: identity/domain). This tracks whether a
 * workload ran — it never determines identity/credit outcomes. An Armada job {@code SUCCEEDED}
 * only means compute finished; the check result comes from the evidence provider.
 */
public enum ValidationExecutionStatus {
    CREATED,
    SUBMISSION_PENDING,
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    UNKNOWN;

    public static ValidationExecutionStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
