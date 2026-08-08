package net.citotech.cito.billing.outbox;

/**
 * An in-process consumer of delivered {@code billing_outbox} events (ADR 0005). {@link OutboxRelay}
 * dispatches each due entry to the first registered handler whose {@link #supports(String)} matches
 * its {@code eventType}; an entry with no matching handler is treated as a processing failure
 * (retried, then eventually parked {@code FAILED}), since there is genuinely nothing that can
 * consume it yet.
 */
public interface OutboxEventHandler {
    boolean supports(String eventType);

    void handle(BillingOutboxEntry entry);
}
