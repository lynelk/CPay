package net.citotech.cito.billing.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/** Append-only JDBC access to {@code billing_usage_events} (Flyway {@code V40}). */
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

    public Optional<UsageEvent> findByIdempotencyKey(String idempotencyKey) {
        SqlParameterSource p = new MapSqlParameterSource("idempotency_key", idempotencyKey);
        List<UsageEvent> rows =
                jdbcTemplate.query(
                        SELECT_SQL + " WHERE idempotency_key=:idempotency_key", p, this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    /**
     * Inserts {@code event} and returns the persisted row, unless a row with the same {@code
     * idempotencyKey} already exists - a caller retry (e.g. a redelivered callback) - in which case
     * the pre-existing row is returned unchanged and nothing new is inserted. Safe under concurrent
     * callers racing the same key: a duplicate-key insert failure is treated the same as finding
     * the row up front, never propagated as an error.
     */
    public UsageEvent insertIfAbsent(UsageEvent event) {
        Optional<UsageEvent> existing = findByIdempotencyKey(event.idempotencyKey());
        if (existing.isPresent()) {
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
            return findByIdempotencyKey(event.idempotencyKey()).orElseThrow(() -> raced);
        }

        return findByIdempotencyKey(event.idempotencyKey())
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Inserted usage event not found: "
                                                + event.idempotencyKey()));
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
