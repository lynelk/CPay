package net.citotech.cito.billing.usage;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * A row from {@code billing_usage_events} (Flyway {@code V40}). {@code id}/{@code createdAt} are
 * {@code 0}/{@code null} on a not-yet-persisted instance passed into {@link
 * UsageEventRepository#insertIfAbsent(UsageEvent)}.
 */
public record UsageEvent(
        long id,
        long billingTenantId,
        String serviceCode,
        String meterCode,
        Instant eventTime,
        BigDecimal quantity,
        String currency,
        Map<String, String> dimensions,
        String sourceReference,
        String idempotencyKey,
        Instant createdAt) {}
