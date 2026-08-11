package net.citotech.cito.communication.config;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-provider delivery policies for {@code communication_provider_policies} (V54, track B6,
 * ISO/IEC 27001 A.8.6 rate limiting). A policy row caps the outbound rate ({@code max_per_minute},
 * {@code max_per_hour}) and timeouts so a misconfigured campaign or provider outage cannot hammer
 * the gateway or the provider. Reads default to the seeded row for {@code LEGACY_SETTINGS} when no
 * policy exists — a provider without an explicit policy inherits the conservative legacy defaults
 * (60/min, 1000/hr), never unbounded.
 */
@Service
public class ProviderPolicyService {

    private static final String FALLBACK_PROVIDER = "LEGACY_SETTINGS";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderPolicyService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The effective policy for a provider code, falling back to LEGACY_SETTINGS when unset. */
    public PolicyRow policyFor(String providerCode) {
        String code =
                providerCode == null || providerCode.isBlank()
                        ? FALLBACK_PROVIDER
                        : providerCode.trim().toUpperCase();
        Optional<PolicyRow> row = findRow(code);
        if (row.isPresent()) {
            return row.get();
        }
        return findRow(FALLBACK_PROVIDER)
                .orElseGet(
                        () ->
                                new PolicyRow(
                                        FALLBACK_PROVIDER,
                                        60,
                                        1000,
                                        10000,
                                        30000,
                                        true,
                                        true,
                                        null,
                                        null));
    }

    /** All policy rows, provider order. */
    public List<PolicyRow> list() {
        return jdbcTemplate.query(
                "SELECT provider_code, max_per_minute, max_per_hour, connect_timeout_ms,"
                        + " read_timeout_ms, rate_limit_flag, enabled_flag, created_at, updated_at"
                        + " FROM communication_provider_policies ORDER BY provider_code ASC",
                new MapSqlParameterSource(),
                this::mapRow);
    }

    /** Upserts one policy row. Returns the persisted row. */
    public PolicyRow save(
            String providerCode,
            Integer maxPerMinute,
            Integer maxPerHour,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            Boolean rateLimit) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new PaymentGatewayException("providerCode is required");
        }
        String code = providerCode.trim().toUpperCase();
        int perMinute = maxPerMinute == null ? 60 : Math.max(1, maxPerMinute);
        int perHour = maxPerHour == null ? 1000 : Math.max(1, maxPerHour);
        boolean rateLimited = rateLimit == null || rateLimit;
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", code);
        p.addValue("max_per_minute", perMinute);
        p.addValue("max_per_hour", perHour);
        p.addValue("connect_timeout_ms", connectTimeoutMs == null ? 10000 : connectTimeoutMs);
        p.addValue("read_timeout_ms", readTimeoutMs == null ? 30000 : readTimeoutMs);
        p.addValue("rate_limit_flag", rateLimited ? "Y" : "N");
        jdbcTemplate.update(
                "INSERT INTO communication_provider_policies (provider_code, max_per_minute,"
                        + " max_per_hour, connect_timeout_ms, read_timeout_ms, rate_limit_flag)"
                        + " VALUES (:provider_code, :max_per_minute, :max_per_hour,"
                        + " :connect_timeout_ms, :read_timeout_ms, :rate_limit_flag)"
                        + " ON DUPLICATE KEY UPDATE max_per_minute=VALUES(max_per_minute),"
                        + " max_per_hour=VALUES(max_per_hour),"
                        + " connect_timeout_ms=VALUES(connect_timeout_ms),"
                        + " read_timeout_ms=VALUES(read_timeout_ms),"
                        + " rate_limit_flag=VALUES(rate_limit_flag)",
                p);
        return policyFor(code);
    }

    private Optional<PolicyRow> findRow(String providerCode) {
        List<PolicyRow> rows =
                jdbcTemplate.query(
                        "SELECT provider_code, max_per_minute, max_per_hour, connect_timeout_ms,"
                                + " read_timeout_ms, rate_limit_flag, enabled_flag, created_at, updated_at"
                                + " FROM communication_provider_policies WHERE provider_code=:provider_code"
                                + " LIMIT 1",
                        new MapSqlParameterSource("provider_code", providerCode),
                        this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private PolicyRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PolicyRow(
                rs.getString("provider_code"),
                rs.getInt("max_per_minute"),
                rs.getInt("max_per_hour"),
                rs.getInt("connect_timeout_ms"),
                rs.getInt("read_timeout_ms"),
                "Y".equals(rs.getString("rate_limit_flag")),
                "Y".equals(rs.getString("enabled_flag")),
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    public record PolicyRow(
            String providerCode,
            int maxPerMinute,
            int maxPerHour,
            int connectTimeoutMs,
            int readTimeoutMs,
            boolean rateLimitEnabled,
            boolean enabled,
            String createdAt,
            String updatedAt) {}
}
