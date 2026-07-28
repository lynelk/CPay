package net.citotech.cito.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB-backed rate-limiter for login/password-reset endpoints (audit E8). Previously in-memory
 * (per-node, reset on restart) and keyed only on the client IP, which is spoofable behind an
 * untrusted proxy. Now backed by the shared {@code api_rate_limits} table (so the limit holds
 * across instances) and keyed on both the account and the IP independently - either budget being
 * exhausted blocks the attempt, so neither "one IP hammering many accounts" nor "one account
 * hammered from many IPs" bypasses the limit.
 */
@Component
public class LoginRateLimiter {

    /** Maximum attempts allowed inside a single time window, per key. */
    static final int MAX_ATTEMPTS = 5;

    /** Length of the fixed window in minutes. */
    static final long WINDOW_MINUTES = 15;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LoginRateLimiter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns {@code true} if the request is within the allowed rate for both the account and
     * the IP, recording the attempt against both. Returns {@code false} when either is exhausted.
     */
    public boolean tryConsume(String account, String clientIp) {
        boolean ipAllowed = consume("ip:" + normalize(clientIp));
        boolean accountAllowed = consume("acct:" + normalize(account));
        return ipAllowed && accountAllowed;
    }

    /** Resets the counters for the given account and IP after a successful login. */
    public void recordSuccess(String account, String clientIp) {
        reset("ip:" + normalize(clientIp));
        reset("acct:" + normalize(account));
    }

    private boolean consume(String rateKey) {
        Instant windowStart = currentWindowStart();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rate_key", rateKey);
        p.addValue("window_start", Timestamp.from(windowStart));
        jdbcTemplate.update(
            "INSERT INTO api_rate_limits (rate_key, window_start, request_count) VALUES (:rate_key, :window_start, 1) "
                + "ON DUPLICATE KEY UPDATE request_count=request_count+1",
            p);
        Integer count = jdbcTemplate.queryForObject(
            "SELECT request_count FROM api_rate_limits WHERE rate_key=:rate_key AND window_start=:window_start",
            p, Integer.class);
        return count == null || count <= MAX_ATTEMPTS;
    }

    private void reset(String rateKey) {
        Instant windowStart = currentWindowStart();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rate_key", rateKey);
        p.addValue("window_start", Timestamp.from(windowStart));
        jdbcTemplate.update("DELETE FROM api_rate_limits WHERE rate_key=:rate_key AND window_start=:window_start", p);
    }

    private Instant currentWindowStart() {
        long epochMinutes = Instant.now().getEpochSecond() / 60;
        long bucketMinutes = (epochMinutes / WINDOW_MINUTES) * WINDOW_MINUTES;
        return Instant.EPOCH.plus(bucketMinutes, ChronoUnit.MINUTES);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
