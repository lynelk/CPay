package net.citotech.cito.communication.preference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Consent audit trail for {@code communication_consent_log} (V51, track B3, ISO/IEC 27001 A.7.1.2
 * signal capture). Every opt-in/opt-out/change for a merchant + channel is appended, never mutated
 * or deleted — the log is the evidence record that a merchant's communication preference was set
 * deliberately and by whom. Tenant-scoped like {@link PreferenceService}.
 */
@Service
public class ConsentService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConsentService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Appends one consent-change record. {@code consentType} (OPT_IN / OPT_OUT / UPDATE) and {@code
     * source} (PORTAL / API / ADMIN / SYSTEM) are validated against the documented sets so the
     * audit trail stays machine-readable.
     */
    public void record(
            long merchantId, String channel, String consentType, String source, String changedBy) {
        String type = normalizeConsentType(consentType);
        String origin = normalizeSource(source);
        String sql =
                "INSERT INTO communication_consent_log (merchant_id, channel, consent_type, source,"
                        + " changed_by) VALUES (:tenant_merchant_id, :channel, :consent_type,"
                        + " :source, :changed_by)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("channel", normalizeChannel(channel));
        p.addValue("consent_type", type);
        p.addValue("source", origin);
        p.addValue("changed_by", changedBy == null ? "system" : changedBy.trim());
        jdbcTemplate.update(sql, p);
    }

    /** The merchant's consent history, most recent first, capped at {@code limit} (max 500). */
    public List<ConsentRow> history(long merchantId, int limit) {
        String sql =
                "SELECT id, merchant_id, channel, consent_type, source, changed_by, created_at"
                        + " FROM communication_consent_log WHERE merchant_id=:tenant_merchant_id"
                        + " ORDER BY id DESC LIMIT :limit";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("limit", Math.max(1, Math.min(limit, 500)));
        return jdbcTemplate.query(sql, p, this::mapRow);
    }

    private String normalizeConsentType(String consentType) {
        if (consentType == null || consentType.isBlank()) {
            throw new PaymentGatewayException("consentType is required");
        }
        String normalized = consentType.trim().toUpperCase();
        if (!List.of("OPT_IN", "OPT_OUT", "UPDATE").contains(normalized)) {
            throw new PaymentGatewayException(
                    "consentType must be OPT_IN, OPT_OUT or UPDATE, got: " + consentType);
        }
        return normalized;
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) {
            throw new PaymentGatewayException("source is required");
        }
        String normalized = source.trim().toUpperCase();
        if (!List.of("PORTAL", "API", "ADMIN", "SYSTEM").contains(normalized)) {
            throw new PaymentGatewayException(
                    "source must be PORTAL, API, ADMIN or SYSTEM, got: " + source);
        }
        return normalized;
    }

    private String normalizeChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new PaymentGatewayException("channel is required");
        }
        return channel.trim().toUpperCase();
    }

    private ConsentRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ConsentRow(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("channel"),
                rs.getString("consent_type"),
                rs.getString("source"),
                rs.getString("changed_by"),
                rs.getString("created_at"));
    }

    public record ConsentRow(
            long id,
            long merchantId,
            String channel,
            String consentType,
            String source,
            String changedBy,
            String createdAt) {}
}
