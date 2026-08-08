package net.citotech.cito.billing.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Writes one {@code billing_outbox} row (ADR 0005). Deliberately carries no {@code @Transactional}
 * annotation of its own: called from inside a caller's existing {@code @Transactional} method, it
 * joins that transaction the same way any other JDBC call on the same thread does, so the row only
 * survives if the caller's transaction commits - the entire point of the outbox pattern. Do not add
 * {@code @Transactional(propagation = REQUIRES_NEW)} here; that would defeat it.
 */
@Service
public class OutboxWriter {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OutboxWriter(NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long write(
            String aggregateType,
            String aggregateId,
            String eventType,
            Map<String, Object> payload) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("aggregate_type", aggregateType);
        p.addValue("aggregate_id", aggregateId);
        p.addValue("event_type", eventType);
        p.addValue("payload", writePayload(payload));
        p.addValue("status", OutboxEntryStatus.PENDING.name());

        jdbcTemplate.update(
                "INSERT INTO billing_outbox (aggregate_type, aggregate_id, event_type, payload, status) "
                        + "VALUES (:aggregate_type, :aggregate_id, :event_type, :payload, :status)",
                p);

        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid outbox payload", e);
        }
    }
}
