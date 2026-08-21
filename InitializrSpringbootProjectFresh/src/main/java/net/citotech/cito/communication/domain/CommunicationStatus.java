package net.citotech.cito.communication.domain;

import java.util.Locale;

/**
 * Lifecycle of a logical communication (ISO domain mapping: communication/domain). Distinct from
 * {@code DeliveryStatus}: this is the parent message's state across channels/attempts, and it
 * deliberately separates provider acceptance from customer delivery. A status change must go
 * through the central transition service - controllers and provider parsers must never flip these
 * values directly.
 */
public enum CommunicationStatus {
    RECEIVED,
    VALIDATING,
    ACCEPTED,
    QUEUED,
    DISPATCHING,
    PROVIDER_ACCEPTED,
    SENT,
    DELIVERED,
    FALLBACK_PENDING,
    FAILED,
    REJECTED,
    UNDELIVERABLE,
    EXPIRED,
    CANCELLED,
    UNKNOWN;

    public static CommunicationStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Terminal states: no further dispatch or callback may advance the message. */
    public boolean isTerminal() {
        return this == DELIVERED
                || this == FAILED
                || this == REJECTED
                || this == UNDELIVERABLE
                || this == EXPIRED
                || this == CANCELLED;
    }

    /** Billable state: provider evidence supports the commercial outcome. */
    public boolean isBillable() {
        return this == PROVIDER_ACCEPTED || this == SENT || this == DELIVERED;
    }
}
