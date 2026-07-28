package net.citotech.cito.admin;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only ops visibility over stuck/failed merchant notification deliveries.
 *
 * <p>Two independent delivery mechanisms exist in this codebase: legacy per-transaction
 * callbacks ({@code callback_tasks}, see {@code net.citotech.cito.callback}) and newer webhook
 * subscriptions ({@code merchant_webhook_deliveries}, see {@code net.citotech.cito.webhook}).
 * This service reports on both without merging them, so ops can see counts-by-status plus the
 * actual rows stuck in a terminal failure state (PARKED for legacy callbacks, FAILED for
 * webhook deliveries) without querying the database by hand.</p>
 */
@Service
public class DeliveryOpsDashboardService {
    private static final List<String> CALLBACK_STATUSES = List.of("PENDING", "RETRY", "PARKED", "DONE");
    private static final List<String> WEBHOOK_STATUSES = List.of("PENDING", "DELIVERED", "FAILED");
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DeliveryOpsDashboardService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DeliveryOpsSummary summary(int limit) {
        int effectiveLimit = clampLimit(limit);
        LegacyCallbackSection legacyCallbacks = new LegacyCallbackSection(
            countsByStatus("callback_tasks", "task_status", CALLBACK_STATUSES),
            stuckCallbacks(effectiveLimit));
        WebhookDeliverySection webhookDeliveries = new WebhookDeliverySection(
            countsByStatus("merchant_webhook_deliveries", "delivery_status", WEBHOOK_STATUSES),
            stuckWebhookDeliveries(effectiveLimit));
        return new DeliveryOpsSummary(legacyCallbacks, webhookDeliveries);
    }

    private List<CallbackDeliveryRow> stuckCallbacks(int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit);
        return jdbcTemplate.query(
            "SELECT t.id, t.merchant_id, m.name AS merchant_name, t.transaction_id, t.reference_value, "
                + "t.task_status, t.attempt_count, t.attempt_limit, t.message, t.next_run_at, t.last_run_at "
                + "FROM callback_tasks t "
                + "LEFT JOIN merchants m ON m.id = t.merchant_id "
                + "WHERE t.task_status = 'PARKED' "
                + "ORDER BY t.last_run_at DESC, t.id DESC LIMIT :limit",
            p,
            (rs, rowNum) -> new CallbackDeliveryRow(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("merchant_name"),
                rs.getString("transaction_id"),
                rs.getString("reference_value"),
                rs.getString("task_status"),
                rs.getInt("attempt_count"),
                rs.getInt("attempt_limit"),
                rs.getString("message"),
                rs.getTimestamp("next_run_at") == null ? null : rs.getTimestamp("next_run_at").toInstant(),
                rs.getTimestamp("last_run_at") == null ? null : rs.getTimestamp("last_run_at").toInstant()));
    }

    private List<WebhookDeliveryRow> stuckWebhookDeliveries(int limit) {
        MapSqlParameterSource p = new MapSqlParameterSource("limit", limit);
        return jdbcTemplate.query(
            "SELECT d.id, d.merchant_id, m.name AS merchant_name, d.endpoint_id, d.event_type, d.event_reference, "
                + "d.delivery_status, d.attempt_count, d.last_http_status, d.last_response_summary, d.next_attempt_at "
                + "FROM merchant_webhook_deliveries d "
                + "LEFT JOIN merchants m ON m.id = d.merchant_id "
                + "WHERE d.delivery_status = 'FAILED' "
                + "ORDER BY d.updated_at DESC, d.id DESC LIMIT :limit",
            p,
            (rs, rowNum) -> new WebhookDeliveryRow(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("merchant_name"),
                rs.getLong("endpoint_id"),
                rs.getString("event_type"),
                rs.getString("event_reference"),
                rs.getString("delivery_status"),
                rs.getInt("attempt_count"),
                rs.getObject("last_http_status") == null ? null : rs.getInt("last_http_status"),
                rs.getString("last_response_summary"),
                rs.getTimestamp("next_attempt_at") == null ? null : rs.getTimestamp("next_attempt_at").toInstant()));
    }

    private Map<String, Integer> countsByStatus(String table, String statusColumn, List<String> knownStatuses) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String status : knownStatuses) {
            counts.put(status, 0);
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT " + statusColumn + " AS status, COUNT(*) AS cnt FROM " + table + " GROUP BY " + statusColumn,
            new MapSqlParameterSource());
        for (Map<String, Object> row : rows) {
            Object statusValue = row.get("status");
            Object countValue = row.get("cnt");
            if (statusValue == null) {
                continue;
            }
            int count = countValue instanceof Number number ? number.intValue() : 0;
            counts.put(statusValue.toString(), count);
        }
        return counts;
    }

    private int clampLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    public record DeliveryOpsSummary(LegacyCallbackSection legacyCallbacks, WebhookDeliverySection webhookDeliveries) {
    }

    public record LegacyCallbackSection(Map<String, Integer> countsByStatus, List<CallbackDeliveryRow> stuck) {
    }

    public record WebhookDeliverySection(Map<String, Integer> countsByStatus, List<WebhookDeliveryRow> stuck) {
    }

    public record CallbackDeliveryRow(
        long id,
        long merchantId,
        String merchantName,
        String transactionId,
        String referenceValue,
        String taskStatus,
        int attemptCount,
        int attemptLimit,
        String message,
        Instant nextRunAt,
        Instant lastRunAt) {
    }

    public record WebhookDeliveryRow(
        long id,
        long merchantId,
        String merchantName,
        long endpointId,
        String eventType,
        String eventReference,
        String deliveryStatus,
        int attemptCount,
        Integer lastHttpStatus,
        String lastResponseSummary,
        Instant nextAttemptAt) {
    }
}
