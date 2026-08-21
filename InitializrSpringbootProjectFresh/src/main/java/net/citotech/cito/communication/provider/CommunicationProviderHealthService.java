package net.citotech.cito.communication.provider;

import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Provider health + circuit state for the Communications Gateway (Track A P6, guide Step 19).
 * Health is observed from real dispatch outcomes — every send result recorded here moves the
 * provider's {@code communication_provider_health} (V82) row between HEALTHY / DEGRADED /
 * UNAVAILABLE with a temporary {@code circuit_open_until} once consecutive failures cross the
 * configured threshold.
 *
 * <p>Circuit semantics (guide Step 19): a simple database-backed breaker — consecutive-failure
 * threshold plus {@code circuit_open_until}. No new dependency; state survives restarts because
 * it lives in MySQL, and multiple app instances converge on the same row. {@link #isOpen} lazily
 * expires an open circuit on read, so recovery requires no sweeper job.
 */
@Service
public class CommunicationProviderHealthService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final int failureThreshold;
    private final int circuitOpenSeconds;

    public CommunicationProviderHealthService(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${cpay.communication.routing.circuit-failure-threshold:5}") int failureThreshold,
            @Value("${cpay.communication.routing.circuit-open-seconds:60}") int circuitOpenSeconds) {
        this.jdbcTemplate = jdbcTemplate;
        this.failureThreshold = Math.max(1, failureThreshold);
        this.circuitOpenSeconds = Math.max(1, circuitOpenSeconds);
    }

    /**
     * Records one dispatch outcome. Success resets the failure counter and closes any open
     * circuit; failure increments it and opens the circuit at the threshold.
     */
    public void record(String providerCode, String channel, boolean success) {
        if (providerCode == null || providerCode.isBlank()
                || channel == null || channel.isBlank()) {
            return;
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("provider_code", providerCode.trim().toUpperCase())
                        .addValue("channel", channel.trim().toUpperCase())
                        .addValue("threshold", failureThreshold)
                        .addValue("open_seconds", circuitOpenSeconds);
        jdbcTemplate.update(
                "INSERT INTO communication_provider_health"
                        + " (provider_code, channel, state, consecutive_failures,"
                        + " circuit_open_until, last_success_at, last_failure_at)"
                        + " VALUES (:provider_code, :channel,"
                        + " IF(:success, 'HEALTHY',"
                        + "     IF(1 >= :threshold, 'UNAVAILABLE', 'DEGRADED')),"
                        + " IF(:success, 0, 1),"
                        + " IF(NOT :success AND 1 >= :threshold,"
                        + "     DATE_ADD(NOW(), INTERVAL :open_seconds SECOND), NULL),"
                        + " IF(:success, NOW(), NULL),"
                        + " IF(:success, NULL, NOW()))"
                        + " ON DUPLICATE KEY UPDATE"
                        + " consecutive_failures = IF(:success, 0, consecutive_failures + 1),"
                        + " state = IF(:success, 'HEALTHY',"
                        + "   IF(consecutive_failures + 1 >= :threshold, 'UNAVAILABLE', 'DEGRADED')),"
                        + " circuit_open_until = IF(:success, NULL,"
                        + "   IF(consecutive_failures + 1 >= :threshold,"
                        + "      DATE_ADD(NOW(), INTERVAL :open_seconds SECOND),"
                        + "      circuit_open_until)),"
                        + " last_success_at = IF(:success, NOW(), last_success_at),"
                        + " last_failure_at = IF(:success, last_failure_at, NOW())",
                p.addValue("success", success));
    }

    /** True when the circuit for this provider/channel is currently open. */
    public boolean isOpen(String providerCode, String channel) {
        if (providerCode == null || channel == null) {
            return false;
        }
        List<Integer> rows =
                jdbcTemplate.query(
                        "SELECT COUNT(*) FROM communication_provider_health"
                                + " WHERE provider_code=:provider_code AND channel=:channel"
                                + " AND circuit_open_until IS NOT NULL"
                                + " AND circuit_open_until > NOW()",
                        new MapSqlParameterSource()
                                .addValue("provider_code", providerCode.trim().toUpperCase())
                                .addValue("channel", channel.trim().toUpperCase()),
                        (rs, rowNum) -> rs.getInt(1));
        return !rows.isEmpty() && rows.get(0) > 0;
    }

    /** The current health row, or empty when the provider has no observed traffic yet. */
    public Optional<HealthRow> find(String providerCode, String channel) {
        List<HealthRow> rows =
                jdbcTemplate.query(
                        "SELECT provider_code, channel, state, consecutive_failures,"
                                + " circuit_open_until, last_success_at, last_failure_at"
                                + " FROM communication_provider_health"
                                + " WHERE provider_code=:provider_code AND channel=:channel LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("provider_code", providerCode.trim().toUpperCase())
                                .addValue("channel", channel.trim().toUpperCase()),
                        (rs, rowNum) ->
                                new HealthRow(
                                        rs.getString("provider_code"),
                                        rs.getString("channel"),
                                        rs.getString("state"),
                                        rs.getInt("consecutive_failures"),
                                        rs.getTimestamp("circuit_open_until") == null
                                                ? null
                                                : rs.getTimestamp("circuit_open_until").toInstant(),
                                        rs.getTimestamp("last_success_at") == null
                                                ? null
                                                : rs.getTimestamp("last_success_at").toInstant(),
                                        rs.getTimestamp("last_failure_at") == null
                                                ? null
                                                : rs.getTimestamp("last_failure_at").toInstant()));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /** All health rows (admin provider-health view, guide Step 41). */
    public List<HealthRow> list() {
        return jdbcTemplate.query(
                "SELECT provider_code, channel, state, consecutive_failures, circuit_open_until,"
                        + " last_success_at, last_failure_at FROM communication_provider_health"
                        + " ORDER BY provider_code ASC, channel ASC",
                new MapSqlParameterSource(),
                (rs, rowNum) ->
                        new HealthRow(
                                rs.getString("provider_code"),
                                rs.getString("channel"),
                                rs.getString("state"),
                                rs.getInt("consecutive_failures"),
                                rs.getTimestamp("circuit_open_until") == null
                                        ? null
                                        : rs.getTimestamp("circuit_open_until").toInstant(),
                                rs.getTimestamp("last_success_at") == null
                                        ? null
                                        : rs.getTimestamp("last_success_at").toInstant(),
                                rs.getTimestamp("last_failure_at") == null
                                        ? null
                                        : rs.getTimestamp("last_failure_at").toInstant()));
    }

    /** One provider/channel health snapshot (V82). */
    public record HealthRow(
            String providerCode,
            String channel,
            String state,
            int consecutiveFailures,
            java.time.Instant circuitOpenUntil,
            java.time.Instant lastSuccessAt,
            java.time.Instant lastFailureAt) {}
}
