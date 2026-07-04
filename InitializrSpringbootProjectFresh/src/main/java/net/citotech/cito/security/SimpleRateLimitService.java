package net.citotech.cito.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SimpleRateLimitService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SimpleRateLimitService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean allow(String key, int maxRequestsPerMinute) {
        Instant window = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("rate_key", key);
        p.addValue("window_start", Timestamp.from(window));
        try {
            jdbcTemplate.update("INSERT INTO api_rate_limits (rate_key, window_start, request_count) VALUES (:rate_key, :window_start, 1)", p);
            return true;
        } catch (Exception ignored) {
            jdbcTemplate.update("UPDATE api_rate_limits SET request_count=request_count+1 WHERE rate_key=:rate_key AND window_start=:window_start", p);
            Integer count = jdbcTemplate.queryForObject("SELECT request_count FROM api_rate_limits WHERE rate_key=:rate_key AND window_start=:window_start", p, Integer.class);
            return count == null || count <= maxRequestsPerMinute;
        }
    }
}

