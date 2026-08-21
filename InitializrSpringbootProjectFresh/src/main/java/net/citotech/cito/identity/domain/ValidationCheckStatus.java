package net.citotech.cito.identity.domain;

import java.util.Locale;

/** Lifecycle of a single validation check within a case (ISO domain mapping: identity/domain). */
public enum ValidationCheckStatus {
    PENDING,
    PROCESSING,
    PASSED,
    FAILED,
    INCONCLUSIVE,
    ERROR,
    SKIPPED;

    public static ValidationCheckStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
