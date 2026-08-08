package net.citotech.cito.billing.outbox;

import java.time.Instant;
import java.util.Map;

/** A row from {@code billing_outbox} (Flyway {@code V41}, ADR 0005). */
public record BillingOutboxEntry(
        long id,
        String aggregateType,
        String aggregateId,
        String eventType,
        Map<String, Object> payload,
        OutboxEntryStatus status,
        int attemptCount,
        Instant nextAttemptAt,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {}
