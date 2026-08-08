package net.citotech.cito.billing.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Covers {@link OutboxRelay}'s claim/dispatch/retry-backoff/poison-message logic against a mocked
 * {@code NamedParameterJdbcTemplate}. See {@code OutboxWriterTestcontainersTest} for this package's
 * real-database precedent; a relay-level Testcontainers test is left to a later slice once a real
 * {@link OutboxEventHandler} is wired (Phase 2), since nothing consumes outbox entries yet.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class OutboxRelayTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void processBatchDeliversAnEntryWhenItsHandlerSucceeds() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubFindDue(jdbcTemplate, entryRow(1L, "USAGE_EVENT_RECORDED", 0));
        when(jdbcTemplate.update(contains("PROCESSING"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        FakeHandler handler = new FakeHandler("USAGE_EVENT_RECORDED", null);

        OutboxRelay relay = newRelay(jdbcTemplate, handler);
        int processed = relay.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(handler.invocations.get()).isEqualTo(1);
        verify(jdbcTemplate)
                .update(
                        contains("DELIVERED"),
                        argThat((MapSqlParameterSource p) -> p.getValue("id").equals(1L)));
    }

    @Test
    void processBatchRetriesWithBackoffWhenTheHandlerThrows() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubFindDue(jdbcTemplate, entryRow(2L, "USAGE_EVENT_RECORDED", 0));
        when(jdbcTemplate.update(contains("PROCESSING"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        FakeHandler handler = new FakeHandler("USAGE_EVENT_RECORDED", new RuntimeException("boom"));

        OutboxRelay relay = newRelay(jdbcTemplate, handler);
        relay.processBatch(10);

        verify(jdbcTemplate)
                .update(
                        contains("status='PENDING', attempt_count"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        p.getValue("id").equals(2L)
                                                && p.getValue("attempt_count").equals(1)
                                                && "boom".equals(p.getValue("last_error"))
                                                && p.getValue("next_attempt_at") != null));
    }

    @Test
    void processBatchParksAsFailedAfterMaxAttempts() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubFindDue(jdbcTemplate, entryRow(3L, "USAGE_EVENT_RECORDED", 4));
        when(jdbcTemplate.update(contains("PROCESSING"), any(MapSqlParameterSource.class)))
                .thenReturn(1);
        FakeHandler handler =
                new FakeHandler("USAGE_EVENT_RECORDED", new RuntimeException("always fails"));

        OutboxRelay relay = newRelay(jdbcTemplate, handler);
        relay.processBatch(10);

        verify(jdbcTemplate)
                .update(
                        contains("status='FAILED'"),
                        argThat(
                                (MapSqlParameterSource p) ->
                                        p.getValue("id").equals(3L)
                                                && p.getValue("attempt_count").equals(5)));
        verify(jdbcTemplate, never())
                .update(contains("status='DELIVERED'"), any(MapSqlParameterSource.class));
    }

    @Test
    void processBatchTreatsAnUnhandledEventTypeAsAFailure() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubFindDue(jdbcTemplate, entryRow(4L, "SOME_UNKNOWN_EVENT", 0));
        when(jdbcTemplate.update(contains("PROCESSING"), any(MapSqlParameterSource.class)))
                .thenReturn(1);

        OutboxRelay relay = newRelay(jdbcTemplate);
        relay.processBatch(10);

        verify(jdbcTemplate)
                .update(
                        contains("status='PENDING', attempt_count"),
                        argThat((MapSqlParameterSource p) -> p.getValue("id").equals(4L)));
    }

    @Test
    void processBatchSkipsAnEntryAlreadyClaimedByAnotherWorker() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        stubFindDue(jdbcTemplate, entryRow(5L, "USAGE_EVENT_RECORDED", 0));
        when(jdbcTemplate.update(contains("PROCESSING"), any(MapSqlParameterSource.class)))
                .thenReturn(0);
        FakeHandler handler = new FakeHandler("USAGE_EVENT_RECORDED", null);

        OutboxRelay relay = newRelay(jdbcTemplate, handler);
        int processed = relay.processBatch(10);

        assertThat(processed).isZero();
        assertThat(handler.invocations.get()).isZero();
        verify(jdbcTemplate, times(1))
                .update(contains("PROCESSING"), any(MapSqlParameterSource.class));
    }

    private OutboxRelay newRelay(
            NamedParameterJdbcTemplate jdbcTemplate, OutboxEventHandler... handlers) {
        OutboxRelay relay = new OutboxRelay(jdbcTemplate, objectMapper, List.of(handlers));
        ReflectionTestUtils.setField(relay, "batchSize", 50);
        ReflectionTestUtils.setField(relay, "maxAttempts", 5);
        ReflectionTestUtils.setField(relay, "backoffBaseSeconds", 30L);
        return relay;
    }

    private void stubFindDue(NamedParameterJdbcTemplate jdbcTemplate, ResultSet row) {
        when(jdbcTemplate.query(
                        contains("FROM billing_outbox"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            return List.of(mapper.mapRow(row, 1));
                        });
    }

    private ResultSet entryRow(long id, String eventType, int attemptCount) throws Exception {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getString("aggregate_type")).thenReturn("USAGE_EVENT");
        when(rs.getString("aggregate_id")).thenReturn("agg-" + id);
        when(rs.getString("event_type")).thenReturn(eventType);
        when(rs.getString("payload")).thenReturn("{}");
        when(rs.getString("status")).thenReturn("PENDING");
        when(rs.getInt("attempt_count")).thenReturn(attemptCount);
        when(rs.getTimestamp("next_attempt_at")).thenReturn(Timestamp.from(Instant.now()));
        when(rs.getString("last_error")).thenReturn(null);
        when(rs.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.now()));
        when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(Instant.now()));
        return rs;
    }

    private static final class FakeHandler implements OutboxEventHandler {
        private final String supportedEventType;
        private final RuntimeException failure;
        private final AtomicInteger invocations = new AtomicInteger();

        private FakeHandler(String supportedEventType, RuntimeException failure) {
            this.supportedEventType = supportedEventType;
            this.failure = failure;
        }

        @Override
        public boolean supports(String eventType) {
            return supportedEventType.equals(eventType);
        }

        @Override
        public void handle(BillingOutboxEntry entry) {
            invocations.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
        }
    }
}
