package net.citotech.cito.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers audit E8: the limiter must be DB-backed (shared across instances, survives restarts)
 * and keyed on both account and IP independently, not IP alone.
 */
class LoginRateLimiterTest {

    @Test
    void allowsRequestsUnderTheLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), any(MapSqlParameterSource.class), eq(Integer.class)))
            .thenReturn(1);
        LoginRateLimiter limiter = new LoginRateLimiter(jdbcTemplate);

        assertThat(limiter.tryConsume("merchant@example.com", "10.0.0.1")).isTrue();
        verify(jdbcTemplate).update(contains("INSERT"), argThat(
            (MapSqlParameterSource p) -> "ip:10.0.0.1".equals(p.getValue("rate_key"))));
        verify(jdbcTemplate).update(contains("INSERT"), argThat(
            (MapSqlParameterSource p) -> "acct:merchant@example.com".equals(p.getValue("rate_key"))));
    }

    @Test
    void blocksOnceTheIpBudgetIsExhaustedEvenForADifferentAccount() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), argThat(
                (MapSqlParameterSource p) -> String.valueOf(p.getValue("rate_key")).startsWith("ip:")),
                eq(Integer.class)))
            .thenReturn(6);
        when(jdbcTemplate.queryForObject(any(String.class), argThat(
                (MapSqlParameterSource p) -> String.valueOf(p.getValue("rate_key")).startsWith("acct:")),
                eq(Integer.class)))
            .thenReturn(1);
        LoginRateLimiter limiter = new LoginRateLimiter(jdbcTemplate);

        assertThat(limiter.tryConsume("victim@example.com", "10.0.0.1")).isFalse();
    }

    @Test
    void blocksOnceTheAccountBudgetIsExhaustedEvenFromADifferentIp() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.queryForObject(any(String.class), argThat(
                (MapSqlParameterSource p) -> String.valueOf(p.getValue("rate_key")).startsWith("ip:")),
                eq(Integer.class)))
            .thenReturn(1);
        when(jdbcTemplate.queryForObject(any(String.class), argThat(
                (MapSqlParameterSource p) -> String.valueOf(p.getValue("rate_key")).startsWith("acct:")),
                eq(Integer.class)))
            .thenReturn(6);
        LoginRateLimiter limiter = new LoginRateLimiter(jdbcTemplate);

        assertThat(limiter.tryConsume("victim@example.com", "203.0.113.9")).isFalse();
    }

    @Test
    void recordSuccessDeletesBothCounters() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        LoginRateLimiter limiter = new LoginRateLimiter(jdbcTemplate);

        limiter.recordSuccess("merchant@example.com", "10.0.0.1");

        verify(jdbcTemplate).update(contains("DELETE"), argThat(
            (MapSqlParameterSource p) -> "ip:10.0.0.1".equals(p.getValue("rate_key"))));
        verify(jdbcTemplate).update(contains("DELETE"), argThat(
            (MapSqlParameterSource p) -> "acct:merchant@example.com".equals(p.getValue("rate_key"))));
    }
}
