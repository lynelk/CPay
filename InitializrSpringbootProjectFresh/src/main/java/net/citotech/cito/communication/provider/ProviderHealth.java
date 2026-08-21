package net.citotech.cito.communication.provider;

import java.time.Instant;

/**
 * Health signal of a provider adapter (ISO domain mapping: communication/provider). Used by the
 * future intelligent router's hard filters: a provider whose circuit is open or whose state is
 * {@code DISABLED}/{@code UNAVAILABLE} must be excluded before scoring.
 */
public record ProviderHealth(State state, Instant lastSuccessAt, Instant lastFailureAt) {

    public enum State {
        HEALTHY,
        DEGRADED,
        UNAVAILABLE,
        DISABLED,
        UNKNOWN
    }
}
