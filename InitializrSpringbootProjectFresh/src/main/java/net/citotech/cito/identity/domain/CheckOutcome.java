package net.citotech.cito.identity.domain;

/**
 * Normalized provider/check outcome (ISO domain mapping: identity/domain). Critical rule: {@code
 * ERROR} and {@code FAIL} are not interchangeable — a provider timeout, OAuth failure, or
 * connection problem must never become an identity or credit rejection.
 */
public enum CheckOutcome {
    PASS,
    FAIL,
    INCONCLUSIVE,
    ERROR,
    PENDING
}
