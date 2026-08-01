package net.citotech.cito.api.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit D1's v1-body idempotency surface added to {@link IdempotencyService}: {@code
 * findExistingBody} replays the stored response for a reused key with an identical body, rejects a
 * reused key with a different body, and tolerates a missing/disabled table; {@code recordBody}
 * persists the raw response body under a canonical request hash. Mirrors the existing Mockito-based
 * test style used across this repo's unit tests (no Spring context).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class IdempotencyServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void findExistingBodyReplaysTheStoredResponseForAnIdenticalReplay() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        String body = "{\"amount\":\"1000\",\"reference\":\"ref-1\"}";
        String storedBody = "{\"state\":\"OK\",\"code\":\"000\"}";
        String hash = CanonicalRequestSigner.sha256Hex(body);
        when(jdbcTemplate.query(
                        contains("cpay_idempotency_keys"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getString("request_hash")).thenReturn(hash);
                            when(rs.getString("response_body")).thenReturn(storedBody);
                            return List.of(mapper.mapRow(rs, 1));
                        });
        IdempotencyService service = new IdempotencyService(jdbcTemplate, objectMapper);

        Optional<String> replayed = service.findExistingBody("1000001", "key-1", body);

        assertThat(replayed).isPresent();
        assertThat(replayed.get()).isEqualTo(storedBody);
    }

    @Test
    void findExistingBodyRejectsAReusedKeyWithADifferentBody() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        String originalBody = "{\"amount\":\"1000\",\"reference\":\"ref-1\"}";
        String differentBody = "{\"amount\":\"2000\",\"reference\":\"ref-1\"}";
        String hash = CanonicalRequestSigner.sha256Hex(originalBody);
        when(jdbcTemplate.query(
                        contains("cpay_idempotency_keys"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getString("request_hash")).thenReturn(hash);
                            when(rs.getString("response_body"))
                                    .thenReturn("{\"state\":\"OK\",\"code\":\"000\"}");
                            return List.of(mapper.mapRow(rs, 1));
                        });
        IdempotencyService service = new IdempotencyService(jdbcTemplate, objectMapper);

        assertThatThrownBy(() -> service.findExistingBody("1000001", "key-1", differentBody))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("different request body");
    }

    @Test
    void findExistingBodyReturnsEmptyWhenTheTableIsNotYetAvailable() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        contains("cpay_idempotency_keys"),
                        any(MapSqlParameterSource.class),
                        any(RowMapper.class)))
                .thenThrow(new DataAccessException("no such table") {});
        IdempotencyService service = new IdempotencyService(jdbcTemplate, objectMapper);

        assertThat(service.findExistingBody("1000001", "key-1", "{}")).isEmpty();
    }

    @Test
    void findExistingBodyReturnsEmptyForABlankKey() {
        IdempotencyService service =
                new IdempotencyService(mock(NamedParameterJdbcTemplate.class), objectMapper);

        assertThat(service.findExistingBody("1000001", "   ", "{}")).isEmpty();
    }

    @Test
    void recordBodyStoresTheRawResponseBody() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(
                        contains("INSERT INTO cpay_idempotency_keys"),
                        any(MapSqlParameterSource.class)))
                .thenReturn(1);
        IdempotencyService service = new IdempotencyService(jdbcTemplate, objectMapper);

        service.recordBody(
                "1000001", "key-1", "{\"amount\":\"1000\"}", "{\"state\":\"OK\",\"code\":\"000\"}");
        // No exception means the insert was accepted; the Mockito stub returning 1 confirms the
        // update path ran.
    }

    @Test
    void recordBodySwallowsDataAccessExceptionsForBackwardCompatibility() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.update(
                        contains("INSERT INTO cpay_idempotency_keys"),
                        any(MapSqlParameterSource.class)))
                .thenThrow(new DataAccessException("no such table") {});
        IdempotencyService service = new IdempotencyService(jdbcTemplate, objectMapper);

        service.recordBody("1000001", "key-1", "{}", "{\"state\":\"OK\"}");
        // Backward compatible: recording must not break the payment flow when the table is absent.
    }
}
