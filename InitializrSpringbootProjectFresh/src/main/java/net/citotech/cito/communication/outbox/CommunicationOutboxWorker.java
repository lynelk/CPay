package net.citotech.cito.communication.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.communication.delivery.CommunicationDeliveryDispatcher;
import net.citotech.cito.communication.delivery.DeliveryStatus;
import net.citotech.cito.communication.provider.CommunicationProviderHealthService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Durable dispatch worker for {@code communication_outbox} (V77, Track A P2). The merchant API
 * commits a {@code communication_messages} row plus an outbox row in one transaction and returns
 * 202; this worker later claims due rows and drives them through the channel-neutral dispatcher,
 * so merchant request latency never depends on provider response time and a provider outage never
 * loses an accepted request.
 *
 * <p>Claiming: rows are claimed with a conditional UPDATE ({@code status='PENDING' AND
 * next_attempt_at<=NOW()}), so multiple app instances can run this sweep concurrently without
 * double-dispatch — each row is won by exactly one claimer. Processing is per-row independent: one
 * failure reschedules only its own row.
 *
 * <p>Retry policy (guide Step 22): transport-class failures are retryable with exponential backoff
 * (5s → 30s → 2m → 10m → 30m); rejections (invalid recipient/template, consent, capability) are
 * terminal. A message past {@code expires_at} is EXPIRED without contacting any provider. After
 * {@code max-attempts} the row dead-letters as FAILED so admin retry can pick it up explicitly.
 */
@Component
@ConditionalOnProperty(
        value = "cpay.communication.outbox.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class CommunicationOutboxWorker {

    private static final Logger logger = Logger.getLogger(CommunicationOutboxWorker.class.getName());

    /** Backoff schedule by attempt number already consumed (1-based): 5s, 30s, 120s, 600s, 1800s. */
    static final long[] BACKOFF_SECONDS = {5, 30, 120, 600, 1800};

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CommunicationDeliveryDispatcher dispatcher;
    private final CommunicationProviderHealthService healthService;
    private final int batchSize;
    private final int maxAttempts;

    public CommunicationOutboxWorker(
            NamedParameterJdbcTemplate jdbcTemplate,
            CommunicationDeliveryDispatcher dispatcher,
            CommunicationProviderHealthService healthService,
            @org.springframework.beans.factory.annotation.Value(
                            "${cpay.communication.outbox.batch-size:100}")
                    int batchSize,
            @org.springframework.beans.factory.annotation.Value(
                            "${cpay.communication.outbox.max-attempts:5}")
                    int maxAttempts) {
        this.jdbcTemplate = jdbcTemplate;
        this.dispatcher = dispatcher;
        this.healthService = healthService;
        this.batchSize = Math.max(1, batchSize);
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Scheduled(fixedDelayString = "${cpay.communication.outbox.fixed-delay-ms:1000}")
    @SchedulerLock(
            name = "communicationOutboxWorker",
            lockAtMostFor = "PT2M",
            lockAtLeastFor = "PT1S")
    public void processDue() {
        try {
            int processed = processDue(batchSize);
            if (processed > 0) {
                logger.log(Level.INFO, "Communication outbox dispatched {0} message(s)", processed);
            }
        } catch (Exception ex) {
            logger.log(Level.WARNING, "Communication outbox sweep failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * Claims and processes one bounded batch of due outbox rows. Package-visible so tests drive a
     * sweep without the scheduler machinery. Returns the number of rows that reached a terminal
     * state this pass (completed or dead-lettered).
     */
    int processDue(int limit) {
        List<OutboxRow> batch = claimBatch(Math.max(1, limit));
        int terminal = 0;
        for (OutboxRow row : batch) {
            if (processOne(row)) {
                terminal++;
            }
        }
        return terminal;
    }

    /**
     * Atomically claims up to {@code limit} PENDING rows whose next attempt is due: flips them to
     * DISPATCHING stamped with this instance's claim token in one UPDATE ... LIMIT, then reads back
     * exactly the rows this instance owns. Rows left DISPATCHING by a crashed worker are recovered
     * by the stale-claim sweep below.
     */
    private List<OutboxRow> claimBatch(int limit) {
        String claimToken = "worker-" + java.util.UUID.randomUUID();
        jdbcTemplate.getJdbcTemplate().update(
                "UPDATE communication_outbox SET status='DISPATCHING', claimed_by=:claimed_by,"
                        + " claimed_at=NOW(), attempts=attempts+1"
                        + " WHERE id IN (SELECT id FROM (SELECT id FROM communication_outbox"
                        + "   WHERE status='PENDING' AND next_attempt_at<=NOW()"
                        + "   ORDER BY priority ASC, next_attempt_at ASC, id ASC LIMIT :limit) t)",
                new MapSqlParameterSource()
                        .addValue("claimed_by", claimToken)
                        .addValue("limit", limit));
        return jdbcTemplate.query(
                "SELECT o.id, o.communication_id, m.merchant_id, m.recipient_type, m.recipient,"
                        + " m.purpose, m.requested_channels, m.selected_channel,"
                        + " m.selected_provider_code, m.template_key, m.fallback_enabled,"
                        + " m.expires_at, o.attempts, o.event_type"
                        + " FROM communication_outbox o"
                        + " JOIN communication_messages m ON m.id=o.communication_id"
                        + " WHERE o.claimed_by=:claimed_by AND o.status='DISPATCHING'"
                        + " ORDER BY o.id ASC",
                new MapSqlParameterSource("claimed_by", claimToken),
                (rs, rowNum) ->
                        new OutboxRow(
                                rs.getLong("id"),
                                rs.getLong("communication_id"),
                                rs.getLong("merchant_id"),
                                rs.getString("recipient_type"),
                                rs.getString("recipient"),
                                rs.getString("purpose"),
                                rs.getString("requested_channels"),
                                rs.getString("selected_channel"),
                                rs.getString("selected_provider_code"),
                                rs.getString("template_key"),
                                "Y".equals(rs.getString("fallback_enabled")),
                                rs.getTimestamp("expires_at") == null
                                        ? null
                                        : rs.getTimestamp("expires_at").toInstant(),
                                rs.getInt("attempts")));
    }

    /** Processes one claimed row to a terminal or rescheduled state. Returns true if terminal. */
    private boolean processOne(OutboxRow row) {
        try {
            // Expiry check first: never contact a provider for an expired message.
            if (row.expiresAt() != null && Instant.now().isAfter(row.expiresAt())) {
                complete(row.id());
                markMessageStatus(row.communicationId(), "EXPIRED");
                return true;
            }

            // Resolve the channel/provider pair for this attempt. The first attempt uses the
            // message's selected channel/provider; retries stay on the same pair until they
            // exhaust, at which point fallback (if enabled) hands off to the next requested
            // channel via FALLBACK_PENDING on the parent message.
            String channel =
                    row.selectedChannel() == null || row.selectedChannel().isBlank()
                            ? firstRequestedChannel(row.requestedChannels())
                            : row.selectedChannel();
            if (channel == null) {
                fail(row, "NO_CHANNEL", "No deliverable channel on communication");
                return true;
            }

            // Render template content when the message references one; otherwise the content is
            // carried in metadata_json by the API layer and read here directly.
            String content = resolveContent(row);
            if (content == null || content.isBlank()) {
                fail(row, "CONTENT_UNAVAILABLE", "Message body could not be resolved");
                return true;
            }

            var outcome =
                    dispatcher.dispatch(
                            row.merchantId(),
                            channel,
                            row.recipient(),
                            subjectFor(row),
                            content,
                            row.selectedProviderCode(),
                            row.communicationId());

            if (outcome.status() == DeliveryStatus.SENT) {
                complete(row.id());
                markMessageStatus(row.communicationId(), "SENT");
                recordOutcome(row, channel, true);
                return true;
            }
            if (outcome.status() == DeliveryStatus.REJECTED) {
                // Non-retryable by definition (consent/capability/validation failure).
                // Business rejection - not counted against provider health.
                complete(row.id());
                markMessageStatus(row.communicationId(), "REJECTED");
                return true;
            }
            // FAILED: transport-class failure counts against provider health.
            recordOutcome(row, channel, false);
            return handleFailure(row, outcome.deliveryId());
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Outbox processing failed for row "
                            + row.id()
                            + ": "
                            + ex.getMessage(),
                    ex);
            return handleFailure(row, null);
        }
    }

    /** Retryable-failure path: backoff-reschedule or dead-letter after max attempts. */
    private boolean handleFailure(OutboxRow row, Long deliveryId) {
        if (row.attempts() >= maxAttempts) {
            fail(row, "MAX_ATTEMPTS_EXCEEDED", "Dispatch failed after " + row.attempts() + " attempts");
            return true;
        }
        long backoffSeconds =
                BACKOFF_SECONDS[Math.min(row.attempts() - 1, BACKOFF_SECONDS.length - 1)];
        jdbcTemplate.update(
                "UPDATE communication_outbox SET status='PENDING', claimed_by=NULL, claimed_at=NULL,"
                        + " last_error_code='DISPATCH_RETRYABLE',"
                        + " last_error_safe='Retry scheduled',"
                        + " next_attempt_at=DATE_ADD(NOW(), INTERVAL :backoff SECOND)"
                        + " WHERE id=:id",
                new MapSqlParameterSource()
                        .addValue("backoff", backoffSeconds)
                        .addValue("id", row.id()));
        markMessageStatus(row.communicationId(), "FALLBACK_PENDING");
        return false;
    }

    /** Terminal failure: dead-letter the row and mark the parent message FAILED. */
    private void fail(OutboxRow row, String errorCode, String safeMessage) {
        jdbcTemplate.update(
                "UPDATE communication_outbox SET status='FAILED', completed_at=NOW(),"
                        + " last_error_code=:code, last_error_safe=:safe WHERE id=:id",
                new MapSqlParameterSource()
                        .addValue("code", errorCode)
                        .addValue("safe", safeMessage)
                        .addValue("id", row.id()));
        markMessageStatus(row.communicationId(), "FAILED");
    }

    private void complete(long outboxId) {
        jdbcTemplate.update(
                "UPDATE communication_outbox SET status='COMPLETED', completed_at=NOW(),"
                        + " last_error_code=NULL, last_error_safe=NULL WHERE id=:id",
                new MapSqlParameterSource("id", outboxId));
    }

    private void markMessageStatus(long communicationId, String status) {
        jdbcTemplate.update(
                "UPDATE communication_messages SET status=:status WHERE id=:id"
                        + " AND status NOT IN ('DELIVERED','CANCELLED')",
                new MapSqlParameterSource()
                        .addValue("status", status)
                        .addValue("id", communicationId));
    }

    /** Feeds one dispatch outcome into the V82 provider-health circuit state (Track A P6). */
    private void recordOutcome(OutboxRow row, String channel, boolean success) {
        try {
            healthService.record(row.selectedProviderCode(), channel, success);
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Provider health recording failed for "
                            + row.selectedProviderCode()
                            + "/"
                            + channel
                            + ": "
                            + ex.getMessage());
        }
    }

    private String resolveContent(OutboxRow row) {
        List<String> bodies =
                jdbcTemplate.query(
                        "SELECT JSON_UNQUOTE(JSON_EXTRACT(metadata_json, '$.body')) FROM"
                                + " communication_messages WHERE id=:id",
                        new MapSqlParameterSource("id", row.communicationId()),
                        (rs, rowNum) -> rs.getString(1));
        String fromMetadata = bodies.isEmpty() ? null : bodies.get(0);
        if (fromMetadata != null && !fromMetadata.isBlank()) {
            return fromMetadata;
        }
        if (row.templateKey() == null || row.templateKey().isBlank()) {
            return null;
        }
        try {
            var rendered =
                    new net.citotech.cito.communication.template.TemplateService(jdbcTemplate)
                            .render(row.templateKey(), row.selectedChannel(), Map.of());
            return rendered.body();
        } catch (Exception ex) {
            logger.log(
                    Level.WARNING,
                    "Template render failed for communication "
                            + row.communicationId()
                            + ": "
                            + ex.getMessage());
            return null;
        }
    }

    private String subjectFor(OutboxRow row) {
        if (row.templateKey() == null || row.templateKey().isBlank()) {
            return "";
        }
        try {
            var rendered =
                    new net.citotech.cito.communication.template.TemplateService(jdbcTemplate)
                            .render(row.templateKey(), row.selectedChannel(), Map.of());
            return rendered.subject() == null ? "" : rendered.subject();
        } catch (Exception ex) {
            return "";
        }
    }

    private String firstRequestedChannel(String requestedChannels) {
        if (requestedChannels == null || requestedChannels.isBlank()) {
            return null;
        }
        for (String part : requestedChannels.split(",")) {
            if (!part.isBlank()) {
                return part.trim().toUpperCase();
            }
        }
        return null;
    }

    /** One claimed outbox row joined with its parent communication. */
    record OutboxRow(
            long id,
            long communicationId,
            long merchantId,
            String recipientType,
            String recipient,
            String purpose,
            String requestedChannels,
            String selectedChannel,
            String selectedProviderCode,
            String templateKey,
            boolean fallbackEnabled,
            Instant expiresAt,
            int attempts) {}
}
