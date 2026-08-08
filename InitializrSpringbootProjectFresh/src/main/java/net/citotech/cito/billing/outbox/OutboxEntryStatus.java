package net.citotech.cito.billing.outbox;

/**
 * Lifecycle of a {@code billing_outbox} row (see {@code Docs/Adr/0005-billing-outbox-design.md}).
 */
public enum OutboxEntryStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    FAILED
}
