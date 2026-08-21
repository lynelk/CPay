package net.citotech.cito.identity.domain;

import java.util.Locale;

/** Lifecycle of a validation case (ISO domain mapping: identity/domain). */
public enum ValidationCaseStatus {
    CREATED,
    AWAITING_INPUT,
    READY,
    PROCESSING,
    PENDING_PROVIDER,
    REVIEW_REQUIRED,
    VERIFIED,
    REJECTED,
    INCONCLUSIVE,
    CANCELLED,
    EXPIRED;

    public static ValidationCaseStatus fromString(String value) {
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
        return this == VERIFIED
                || this == REJECTED
                || this == INCONCLUSIVE
                || this == CANCELLED
                || this == EXPIRED;
    }
}
