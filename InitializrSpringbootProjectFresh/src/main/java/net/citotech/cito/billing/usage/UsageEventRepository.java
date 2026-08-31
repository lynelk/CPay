package net.citotech.cito.billing.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** Append-only JDBC access to {@code billing_usage_events} (Flyway {@code V40/V107}). */
@Repository
public class UsageEventRepository {
    private static final String SELECT_SQL =
            "SELECT id, billing_tenant_id, service_code, meter_code, event_time, quantity, "
                    + "currency, dimensions, source_reference, idempotency_key, created_at "
                    + "FROM billing_usage_events";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public UsageEventRepository(
            NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Tenant-scoped lookup. Public/multi-tenant call paths must use this method so the same external
     * idempotency key used by two tenants cannot disclose or suppress the other tenant's event.
     */
    public Optional<UsageEvent> findByIdempotencyKey(long billingTenantId, String idempotencyKey) {
        SqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("billing_tenant_id", billingTenantId)
                        .addValue("idempotency_key", idempotencyKey);
        List<UsageEvent> rows =
                jdbcTemplate.query(
                        SELECT_SQL
                                + " WHERE billing_tenant_id=:billing_tenant_id "
                                + "AND idempotency_key=:idempotency_key",
                        p,
                        this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Legacy global lookup retained only for backward-compatible internal diagnostics. New
     * production paths must use the tenant-scoped overload.
     */
    @Deprecated(forRemoval = false)
    public Optional<UsageEvent> findByIdempotencyKey(String idempotencyKey) {
        SqlParameterSource p = new MapSqlParameterSource("idempotency_key", idempotencyKey);
        List<UsageEvent> rows =
                jdbcTemplate.query(
                        SELECT_SQL + " WHERE idempotency_key=:idempotency_key ORDER BY id LIMIT 2",
                        p,
                        this::mapRow);
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "Usage idempotency key exists in multiple tenants; tenant context is required");
        }
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Inserts {@code event} and returns the tenant's persisted row. The unique key is
     * (billing_tenant_id,idempotency_key), so a retry is idempotent within one tenant without
     * coupling unrelated tenants.
     */
    public UsageEvent insertIfAbsent(UsageEvent event) {
        Optional<UsageEvent> existing =
                findByIdempotencyKey(event.billingTenantId(), event.idempotencyKey());
        if (existing.isPresent()) {
            validateReplay(existing.get(), event);
            return existing.get();
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", event.billingTenantId());
        p.addValue("service_code", event.serviceCode());
        p.addValue("meter_code", event.meterCode());
        p.addValue("event_time", Timestamp.from(event.eventTime()));
        p.addValue("quantity", event.quantity());
        p.addValue("currency", event.currency());
        p.addValue("dimensions", writeDimensions(event.dimensions()));
        p.addValue("source_reference", event.sourceReference());
        p.addValue("idempotency_key", event.idempotencyKey());

        try {
            jdbcTemplate.update(
                    "INSERT INTO billing_usage_events (billing_tenant_id, service_code, meter_code, "
                            + "event_time, quantity, currency, dimensions, source_reference, idempotency_key) "
                            + "VALUES (:billing_tenant_id, :service_code, :meter_code, :event_time, :quantity, "
                            + ":currency, :dimensions, :source_reference, :idempotency_key)",
                    p);
        } catch (DuplicateKeyException raced) {
            UsageEvent winner =
                    findByIdempotencyKey(event.billingTenantId(), event.idempotencyKey())
                            .orElseThrow(() -> raced);
            validateReplay(winner, event);
            return winner;
        }

        return findByIdempotencyKey(event.billingTenantId(), event.idempotencyKey())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Inserted usage event not found: " + event.idempotencyKey()));
    }

    public List<UsageEvent> findForTenant(
            long billingTenantId, Instant from, Instant to, String serviceCode, int limit) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to))
                        .addValue("service", serviceCode)
                        .addValue("limit", Math.max(1, Math.min(limit, 500)));
        String serviceFilter =
                serviceCode == null || serviceCode.isBlank() ? "" : " AND service_code=:service";
        return jdbcTemplate.query(
                SELECT_SQL
                        + " WHERE billing_tenant_id=:tenant AND event_time>=:from AND event_time<:to"
                        + serviceFilter
                        + " ORDER BY event_time DESC,id DESC LIMIT :limit",
                p,
                this::mapRow);
    }

    private void validateReplay(UsageEvent existing, UsageEvent requested) {
        if (existing.billingTenantId() != requested.billingTenantId()
                || !java.util.Objects.equals(existing.serviceCode(), requested.serviceCode())
                || !java.util.Objects.equals(existing.meterCode(), requested.meterCode())
                || existing.quantity().compareTo(requested.quantity()) != 0
                || !java.util.Objects.equals(existing.currency(), requested.currency())
                || !java.util.Objects.equals(existing.sourceReference(), requested.sourceReference())) {
            throw new IllegalArgumentException(
                    "Usage idempotency key was already used with different event attributes");
        }
    }

    private String writeDimensions(Map<String, String> dimensions) {
        if (dimensions == null || dimensions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dimensions);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid usage event dimensions", e);
        }
    }

    private Map<String, String> readDimensions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Corrupt usage event dimensions", e);
        }
    }

    private UsageEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new UsageEvent(
                rs.getLong("id"),
                rs.getLong("billing_tenant_id"),
                rs.getString("service_code"),
                rs.getString("meter_code"),
                rs.getTimestamp("event_time").toInstant(),
                rs.getBigDecimal("quantity"),
                rs.getString("currency"),
                readDimensions(rs.getString("dimensions")),
                rs.getString("source_reference"),
                rs.getString("idempotency_key"),
                rs.getTimestamp("created_at").toInstant());
    }
}
