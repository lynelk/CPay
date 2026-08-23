package net.citotech.cito.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.api.v2.dto.PaymentResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.marketplace.MarketplaceSplitService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable outbox/recovery lane for cross-feature actions that must survive a process crash. */
@Service
public class PlatformFeatureEventService {
    private static final String SPLIT_CAPTURE = "MARKETPLACE_SPLIT_CAPTURE";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MarketplaceSplitService splitService;
    private final CitoFeatureAccessService featureAccessService;
    private final ObjectMapper objectMapper;

    public PlatformFeatureEventService(
            NamedParameterJdbcTemplate jdbcTemplate,
            MarketplaceSplitService splitService,
            CitoFeatureAccessService featureAccessService,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.splitService = splitService;
        this.featureAccessService = featureAccessService;
        this.objectMapper = objectMapper;
    }

    /**
     * Persists split intent before the provider call. Returns null when the payment has no split.
     * The event remains WAITING_PAYMENT until the payment outcome is known.
     */
    @Transactional
    public String prepareSplitCapture(Merchant merchant, PaymentRequest request, String environment) {
        if (merchant == null || merchant.getId() == null || request == null || request.getMetadata() == null) {
            return null;
        }
        String splitRuleReference = request.getMetadata().get("splitRuleReference");
        if (splitRuleReference == null || splitRuleReference.isBlank()) {
            return null;
        }
        String env = featureAccessService.normalizeEnvironment(environment);
        featureAccessService.require(merchant.getId(), "MARKETPLACE_PAYMENTS", env);
        String reference = deterministicReference(
                merchant.getId() + ":" + request.getReference() + ":" + splitRuleReference + ":" + env);
        Map<String, Object> payload = Map.of(
                "splitRuleReference", splitRuleReference.trim(),
                "transactionReference", required(request.getReference(), "reference"),
                "currencyCode", required(request.getCurrency(), "currency"),
                "grossAmount", required(request.getAmount(), "amount"),
                "environment", env);
        try {
            jdbcTemplate.update(
                    "INSERT INTO platform_feature_events "
                            + "(merchant_id, event_reference, event_type, subject_reference, payload_json, status, next_attempt_at) "
                            + "VALUES (:merchant_id, :event_reference, :event_type, :subject_reference, :payload_json, 'WAITING_PAYMENT', CURRENT_TIMESTAMP) "
                            + "ON DUPLICATE KEY UPDATE payload_json=VALUES(payload_json), last_error=NULL",
                    new MapSqlParameterSource()
                            .addValue("merchant_id", merchant.getId())
                            .addValue("event_reference", reference)
                            .addValue("event_type", SPLIT_CAPTURE)
                            .addValue("subject_reference", request.getReference())
                            .addValue("payload_json", objectMapper.writeValueAsString(payload)));
            return reference;
        } catch (Exception e) {
            throw new PaymentGatewayException("Unable to persist marketplace split intent");
        }
    }

    @Transactional
    public void confirmPaymentOutcome(String eventReference, PaymentResult result) {
        if (eventReference == null || eventReference.isBlank()) {
            return;
        }
        String paymentStatus = result == null ? null : result.getStatus();
        if (successful(paymentStatus)) {
            updateWaitingReference(eventReference, "PENDING", null, Instant.now());
            return;
        }
        if (terminalFailure(paymentStatus)) {
            updateWaitingReference(eventReference, "CANCELLED", null, Instant.now());
            return;
        }
        // Accepted, submitted and provider-pending outcomes are deliberately not guessed. The
        // recovery sweep waits for CPay's normalized transaction status/callback before deciding.
        updateWaitingReference(
                eventReference,
                "WAITING_PAYMENT",
                "Payment outcome is pending; waiting for final transaction status",
                Instant.now().plusSeconds(30));
    }

    /** Leaves an ambiguous provider exception recoverable instead of guessing that money did not move. */
    @Transactional
    public void markPaymentOutcomeUnknown(String eventReference, Exception error) {
        if (eventReference == null || eventReference.isBlank()) {
            return;
        }
        updateWaitingReference(
                eventReference,
                "WAITING_PAYMENT",
                safeMessage(error),
                Instant.now().plusSeconds(30));
    }

    @Scheduled(fixedDelayString = "${cpay.platform-feature-events.delay-ms:15000}")
    @SchedulerLock(name = "platformFeatureEvents", lockAtMostFor = "PT2M", lockAtLeastFor = "PT2S")
    public void processScheduled() {
        recoverWaitingPayments(100);
        processDue(100);
    }

    /**
     * Reconciles WAITING_PAYMENT events with CPay's own transaction log. This is intentionally
     * conservative: missing or pending transactions stay waiting until a later status check/callback
     * resolves them. Only explicit success activates split capture; explicit terminal failure cancels it.
     */
    @Transactional
    public int recoverWaitingPayments(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> waiting = jdbcTemplate.queryForList(
                "SELECT id, merchant_id, subject_reference FROM platform_feature_events "
                        + "WHERE event_type=:event_type AND status='WAITING_PAYMENT' AND next_attempt_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY id LIMIT " + safeLimit,
                new MapSqlParameterSource("event_type", SPLIT_CAPTURE));
        int changed = 0;
        for (Map<String, Object> event : waiting) {
            long merchantId = ((Number) event.get("merchant_id")).longValue();
            String reference = String.valueOf(event.get("subject_reference"));
            List<String> statuses = jdbcTemplate.query(
                    "SELECT status FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
                            + " WHERE merchant_id=:merchant_id AND tx_merchant_ref=:reference ORDER BY id DESC LIMIT 1",
                    new MapSqlParameterSource()
                            .addValue("merchant_id", merchantId)
                            .addValue("reference", reference),
                    (rs, rowNum) -> rs.getString("status"));
            if (statuses.isEmpty()) {
                deferWaiting(((Number) event.get("id")).longValue());
                continue;
            }
            String status = statuses.get(0) == null ? "" : statuses.get(0).trim().toUpperCase(Locale.ROOT);
            if (successful(status)) {
                changed += updateWaitingStatus(((Number) event.get("id")).longValue(), "PENDING", null);
            } else if (terminalFailure(status)) {
                changed += updateWaitingStatus(((Number) event.get("id")).longValue(), "CANCELLED", null);
            } else {
                deferWaiting(((Number) event.get("id")).longValue());
            }
        }
        return changed;
    }

    @Transactional
    public int processDue(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        List<Map<String, Object>> events = jdbcTemplate.queryForList(
                "SELECT id, merchant_id, event_reference, event_type, payload_json, attempt_count "
                        + "FROM platform_feature_events WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP "
                        + "ORDER BY id LIMIT " + safeLimit,
                new MapSqlParameterSource());
        int processed = 0;
        for (Map<String, Object> event : events) {
            long id = ((Number) event.get("id")).longValue();
            if (claim(id) == 0) {
                continue;
            }
            try {
                if (SPLIT_CAPTURE.equals(String.valueOf(event.get("event_type")))) {
                    processSplit(event);
                } else {
                    throw new PaymentGatewayException("Unsupported platform feature event type");
                }
                jdbcTemplate.update(
                        "UPDATE platform_feature_events SET status='COMPLETED', processed_at=CURRENT_TIMESTAMP, last_error=NULL WHERE id=:id",
                        new MapSqlParameterSource("id", id));
                processed++;
            } catch (Exception e) {
                int attempts = ((Number) event.get("attempt_count")).intValue() + 1;
                String status = attempts >= 10 ? "FAILED" : "PENDING";
                long delaySeconds = Math.min(3600, 15L * (1L << Math.min(attempts, 7)));
                jdbcTemplate.update(
                        "UPDATE platform_feature_events SET status=:status, attempt_count=:attempt_count, "
                                + "last_error=:last_error, next_attempt_at=:next_attempt_at WHERE id=:id",
                        new MapSqlParameterSource()
                                .addValue("id", id)
                                .addValue("status", status)
                                .addValue("attempt_count", attempts)
                                .addValue("last_error", safeMessage(e))
                                .addValue("next_attempt_at", Timestamp.from(Instant.now().plusSeconds(delaySeconds))));
            }
        }
        return processed;
    }

    public List<Map<String, Object>> recentEvents(long merchantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT event_reference AS eventReference, event_type AS eventType, subject_reference AS subjectReference, "
                        + "status, attempt_count AS attemptCount, last_error AS lastError, created_at AS createdAt, processed_at AS processedAt "
                        + "FROM platform_feature_events WHERE merchant_id=:merchant_id ORDER BY id DESC LIMIT " + safeLimit,
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    private int claim(long id) {
        return jdbcTemplate.update(
                "UPDATE platform_feature_events SET status='PROCESSING' WHERE id=:id AND status='PENDING'",
                new MapSqlParameterSource("id", id));
    }

    private int updateWaitingStatus(long id, String status, String error) {
        return jdbcTemplate.update(
                "UPDATE platform_feature_events SET status=:status, next_attempt_at=CURRENT_TIMESTAMP, last_error=:last_error "
                        + "WHERE id=:id AND status='WAITING_PAYMENT'",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("status", status)
                        .addValue("last_error", error));
    }

    private void updateWaitingReference(
            String eventReference, String status, String error, Instant nextAttemptAt) {
        jdbcTemplate.update(
                "UPDATE platform_feature_events SET status=:status, last_error=:last_error, next_attempt_at=:next_attempt_at "
                        + "WHERE event_reference=:event_reference AND status='WAITING_PAYMENT'",
                new MapSqlParameterSource()
                        .addValue("event_reference", eventReference)
                        .addValue("status", status)
                        .addValue("last_error", error)
                        .addValue("next_attempt_at", Timestamp.from(nextAttemptAt)));
    }

    private void deferWaiting(long id) {
        jdbcTemplate.update(
                "UPDATE platform_feature_events SET next_attempt_at=:next_attempt_at WHERE id=:id AND status='WAITING_PAYMENT'",
                new MapSqlParameterSource()
                        .addValue("id", id)
                        .addValue("next_attempt_at", Timestamp.from(Instant.now().plusSeconds(60))));
    }

    private void processSplit(Map<String, Object> event) throws Exception {
        long merchantId = ((Number) event.get("merchant_id")).longValue();
        Map<String, Object> payload = objectMapper.readValue(
                String.valueOf(event.get("payload_json")), new TypeReference<Map<String, Object>>() {});
        String environment = String.valueOf(payload.get("environment"));
        featureAccessService.require(merchantId, "MARKETPLACE_PAYMENTS", environment);
        splitService.executeSplit(
                merchantId,
                String.valueOf(payload.get("transactionReference")),
                String.valueOf(payload.get("splitRuleReference")),
                String.valueOf(payload.get("currencyCode")),
                new java.math.BigDecimal(String.valueOf(payload.get("grossAmount"))));
    }

    private boolean successful(String value) {
        if (value == null) {
            return false;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        return java.util.Set.of("SUCCESS", "SUCCESSFUL", "COMPLETED", "COMPLETE", "000").contains(status);
    }

    private boolean terminalFailure(String value) {
        if (value == null) {
            return false;
        }
        String status = value.trim().toUpperCase(Locale.ROOT);
        return java.util.Set.of(
                        "FAILED", "FAILURE", "REJECTED", "CANCELLED", "CANCELED", "DECLINED")
                .contains(status);
    }

    private String deterministicReference(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "PFE-" + HexFormat.of().formatHex(digest).substring(0, 48).toUpperCase(Locale.ROOT);
        } catch (Exception e) {
            return "PFE-" + Instant.now().toEpochMilli() + "-" + Math.abs(value.hashCode());
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required for split capture");
        }
        return value.trim();
    }

    private String safeMessage(Exception e) {
        String value = e == null ? "Feature processing failed" : e.getMessage();
        if (value == null || value.isBlank()) {
            value = e == null ? "Feature processing failed" : e.getClass().getSimpleName();
        }
        return value.length() > 900 ? value.substring(0, 900) : value;
    }
}
