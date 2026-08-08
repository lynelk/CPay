package net.citotech.cito.billing.outbox;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls {@code PENDING} {@code billing_outbox} rows (ADR 0005) and dispatches each to the first
 * registered {@link OutboxEventHandler} whose {@code supports(eventType)} matches, following the
 * same ShedLock + {@code @Scheduled} pattern as {@code scheduler/LedgerOperationsScheduler} so
 * exactly one instance processes a batch at a time cluster-wide - no per-row claim token is needed
 * beyond the guarded {@code PENDING -> PROCESSING} update. Every entry this method touches leaves
 * it {@code DELIVERED}, retried back to {@code PENDING} with backoff, or parked {@code FAILED}
 * after {@code maxAttempts} - never left stuck in {@code PROCESSING}.
 */
@Component
@ConditionalOnProperty(
        value = "cpay.billing.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class OutboxRelay {
    private static final Logger logger = Logger.getLogger(OutboxRelay.class.getName());

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final List<OutboxEventHandler> handlers;

    @Value("${cpay.billing.outbox.batch-size:50}")
    private int batchSize;

    @Value("${cpay.billing.outbox.max-attempts:5}")
    private int maxAttempts;

    @Value("${cpay.billing.outbox.backoff-base-seconds:30}")
    private long backoffBaseSeconds;

    public OutboxRelay(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            List<OutboxEventHandler> handlers) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.handlers = handlers;
    }

    @Scheduled(fixedDelayString = "${cpay.billing.outbox.fixed-delay-ms:30000}")
    @SchedulerLock(name = "billingOutboxRelay", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void relayPending() {
        try {
            processBatch(batchSize);
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Billing outbox relay failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Package-visible so tests can drive a batch directly without the {@code @Scheduled} machinery.
     */
    int processBatch(int limit) {
        List<BillingOutboxEntry> due = findDue(limit);
        int processed = 0;
        for (BillingOutboxEntry entry : due) {
            if (claim(entry.id())) {
                processOne(entry);
                processed++;
            }
        }
        return processed;
    }

    private void processOne(BillingOutboxEntry entry) {
        try {
            OutboxEventHandler handler =
                    handlers.stream()
                            .filter(h -> h.supports(entry.eventType()))
                            .findFirst()
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "No handler registered for event type "
                                                            + entry.eventType()));
            handler.handle(entry);
            markDelivered(entry.id());
        } catch (Exception ex) {
            recordFailure(entry, ex.getMessage());
        }
    }

    private List<BillingOutboxEntry> findDue(int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("now", Timestamp.from(Instant.now()));
        p.addValue("limit", limit);
        return jdbcTemplate.query(
                "SELECT id, aggregate_type, aggregate_id, event_type, payload, status, "
                        + "attempt_count, next_attempt_at, last_error, created_at, updated_at "
                        + "FROM billing_outbox "
                        + "WHERE status = 'PENDING' AND next_attempt_at <= :now "
                        + "ORDER BY id LIMIT :limit",
                p,
                this::mapRow);
    }

    private boolean claim(long id) {
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_outbox SET status='PROCESSING' WHERE id=:id AND status='PENDING'",
                        new MapSqlParameterSource("id", id));
        return updated == 1;
    }

    private void markDelivered(long id) {
        jdbcTemplate.update(
                "UPDATE billing_outbox SET status='DELIVERED' WHERE id=:id",
                new MapSqlParameterSource("id", id));
    }

    private void recordFailure(BillingOutboxEntry entry, String errorMessage) {
        int nextAttemptCount = entry.attemptCount() + 1;
        String safeError = errorMessage == null ? "unknown error" : errorMessage;
        String truncatedError = safeError.substring(0, Math.min(500, safeError.length()));

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", entry.id());
        p.addValue("attempt_count", nextAttemptCount);
        p.addValue("last_error", truncatedError);

        if (nextAttemptCount >= maxAttempts) {
            jdbcTemplate.update(
                    "UPDATE billing_outbox SET status='FAILED', attempt_count=:attempt_count, "
                            + "last_error=:last_error WHERE id=:id",
                    p);
            return;
        }

        long backoffSeconds = backoffBaseSeconds * (1L << (nextAttemptCount - 1));
        p.addValue("next_attempt_at", Timestamp.from(Instant.now().plusSeconds(backoffSeconds)));
        jdbcTemplate.update(
                "UPDATE billing_outbox SET status='PENDING', attempt_count=:attempt_count, "
                        + "next_attempt_at=:next_attempt_at, last_error=:last_error WHERE id=:id",
                p);
    }

    private BillingOutboxEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BillingOutboxEntry(
                rs.getLong("id"),
                rs.getString("aggregate_type"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                readPayload(rs.getString("payload")),
                OutboxEntryStatus.valueOf(rs.getString("status")),
                rs.getInt("attempt_count"),
                rs.getTimestamp("next_attempt_at").toInstant(),
                rs.getString("last_error"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private Map<String, Object> readPayload(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Corrupt outbox payload", e);
        }
    }
}
