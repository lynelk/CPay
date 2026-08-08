package net.citotech.cito.billing.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

/**
 * Covers {@link OutboxWriter}'s SQL/serialization shape via a mocked {@code
 * NamedParameterJdbcTemplate}. See {@code OutboxWriterTestcontainersTest} (docker-tagged) for the
 * real proof that a write inside a rolled-back transaction never survives - a mock cannot exercise
 * real transaction semantics.
 */
class OutboxWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writeInsertsAPendingEntryAndReturnsItsGeneratedId() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(11L);

        long id =
                new OutboxWriter(jdbcTemplate, objectMapper)
                        .write("USAGE_EVENT", "agg-1", "USAGE_EVENT_RECORDED", Map.of("k", "v"));

        assertThat(id).isEqualTo(11L);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @Test
    void writeToleratesANullPayload() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(12L);

        long id =
                new OutboxWriter(jdbcTemplate, objectMapper)
                        .write("USAGE_EVENT", "agg-2", "USAGE_EVENT_RECORDED", null);

        assertThat(id).isEqualTo(12L);
    }

    @Test
    void writeReturnsZeroWhenNoGeneratedIdIsAvailable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(
                        anyString(), any(SqlParameterSource.class), eq(Long.class)))
                .thenReturn(null);

        long id =
                new OutboxWriter(jdbcTemplate, objectMapper)
                        .write("USAGE_EVENT", "agg-3", "USAGE_EVENT_RECORDED", Map.of());

        assertThat(id).isZero();
    }
}
