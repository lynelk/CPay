package net.citotech.cito.billing.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Covers the {@code billing_usage_events} (Flyway {@code V40}) idempotent insert path. */
@SuppressWarnings({"rawtypes", "unchecked"})
class UsageEventRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void insertIfAbsentInsertsWhenNoRowExistsForTheIdempotencyKey() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of())
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(usageEventRow(), 1));
                        });

        UsageEvent toInsert = newEvent("key-1");
        UsageEvent result =
                new UsageEventRepository(jdbcTemplate, objectMapper).insertIfAbsent(toInsert);

        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.idempotencyKey()).isEqualTo("key-1");
        verify(jdbcTemplate, times(1)).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void insertIfAbsentReturnsTheExistingRowWithoutInsertingAgain() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(usageEventRow(), 1));
                        });

        UsageEvent result =
                new UsageEventRepository(jdbcTemplate, objectMapper)
                        .insertIfAbsent(newEvent("key-1"));

        assertThat(result.id()).isEqualTo(9L);
        verify(jdbcTemplate, never()).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void insertIfAbsentRecoversFromARaceOnTheUniqueIdempotencyKey() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of())
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(usageEventRow(), 1));
                        });
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate idempotency_key"));

        UsageEvent result =
                new UsageEventRepository(jdbcTemplate, objectMapper)
                        .insertIfAbsent(newEvent("key-1"));

        assertThat(result.id()).isEqualTo(9L);
    }

    private UsageEvent newEvent(String idempotencyKey) {
        return new UsageEvent(
                0L,
                7L,
                "PAYMENT",
                "payment_event_count",
                Instant.now(),
                BigDecimal.ONE,
                "UGX",
                Map.of("provider", "MTN"),
                "tx-123",
                idempotencyKey,
                null);
    }

    private ResultSet usageEventRow() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(9L);
        when(rs.getLong("billing_tenant_id")).thenReturn(7L);
        when(rs.getString("service_code")).thenReturn("PAYMENT");
        when(rs.getString("meter_code")).thenReturn("payment_event_count");
        when(rs.getTimestamp("event_time")).thenReturn(Timestamp.from(Instant.now()));
        when(rs.getBigDecimal("quantity")).thenReturn(BigDecimal.ONE);
        when(rs.getString("currency")).thenReturn("UGX");
        when(rs.getString("dimensions")).thenReturn("{\"provider\":\"MTN\"}");
        when(rs.getString("source_reference")).thenReturn("tx-123");
        when(rs.getString("idempotency_key")).thenReturn("key-1");
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
        return rs;
    }
}
