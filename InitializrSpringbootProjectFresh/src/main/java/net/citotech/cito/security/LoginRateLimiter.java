package net.citotech.cito.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * DB-backed rate limiter for login and password-reset endpoints. The account and source-network
 * budgets are intentionally independent: account throttling resists distributed credential attacks,
 * while a broader IP budget slows password spraying without punishing ordinary NAT/shared-office
 * traffic after only a handful of attempts.
 */
@Component
public class LoginRateLimiter {

    /** Maximum failed/attempted logins for one account in a 15-minute window. */
    static final int ACCOUNT_MAX_ATTEMPTS = 5;

    /** Broader source-IP budget to detect password spraying across many accounts. */
    static final int IP_MAX_ATTEMPTS = 25;

    /** Length of the fixed window in minutes. */
    static final long WINDOW_MINUTES = 15;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LoginRateLimiter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Returns {@code true} only when both the account and source-IP budgets allow the request.
     * Both counters are recorded on every attempt so distributed and spray patterns remain visible.
     */
    public boolean tryConsume(String account, String clientIp) {
        boolean ipAllowed = consume("ip:" + normalize(clientIp), IP_MAX_ATTEMPTS);
        boolean accountAllowed = consume("acct:" + normalize(account), ACCOUNT_MAX_ATTEMPTS);
        return ipAllowed && accountAllowed;
    }

    /**
     * Clears only the account budget after successful authentication. The source-IP budget is kept
     * for the remainder of its window so an attacker cannot use one valid account to repeatedly
     * reset the network-level spraying protection.
     */
    public void recordSuccess(String account, String clientIp) {
        reset("acct:" + normalize(account));
    }

    private boolean consume(String rateKey, int maximumAttempts) {
        Instant windowStart = currentWindowStart();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rate_key", rateKey);
        p.addValue("window_start", Timestamp.from(windowStart));
        jdbcTemplate.update(
                "INSERT INTO api_rate_limits (rate_key, window_start, request_count) VALUES (:rate_key, :window_start, 1) "
                        + "ON DUPLICATE KEY UPDATE request_count=request_count+1",
                p);
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT request_count FROM api_rate_limits WHERE rate_key=:rate_key AND window_start=:window_start",
                        p,
                        Integer.class);
        return count == null || count <= maximumAttempts;
    }

    private void reset(String rateKey) {
        Instant windowStart = currentWindowStart();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rate_key", rateKey);
        p.addValue("window_start", Timestamp.from(windowStart));
        jdbcTemplate.update(
                "DELETE FROM api_rate_limits WHERE rate_key=:rate_key AND window_start=:window_start",
                p);
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
