package net.citotech.cito.communication.preference;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Per-merchant channel preferences for {@code communication_message_preferences} (V51, track B3,
 * ISO domain mapping: communication/preference). A preference row is one merchant + channel +
 * enabled flag + optional quiet-hours window. Reads and writes are tenant-scoped through {@link
 * TenantScopeGuard}, mirroring the merchant_feature_flags pattern — a forced-on schema default with
 * opt-out is the consent-preserving posture (ISO/IEC 27001 A.7.1.2 signal capture lives in the
 * {@link ConsentService} audit log instead).
 */
@Service
public class PreferenceService {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PreferenceService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PreferenceRow> listForMerchant(long merchantId) {
        String sql =
                "SELECT id, merchant_id, channel, enabled_flag, quiet_hours_start, quiet_hours_end,"
                        + " updated_by, created_at, updated_at FROM communication_message_preferences"
                        + " WHERE merchant_id=:tenant_merchant_id ORDER BY channel ASC";
        TenantScopeGuard.assertTenantBound(sql);
        return jdbcTemplate.query(sql, TenantScopeGuard.scope(null, merchantId), this::mapRow);
    }

    /**
     * Whether a channel is enabled for a merchant. Defaults to enabled (Y) when no preference row
     * exists — matching the schema default and the consent-preserving posture: a merchant that was
     * never configured is treated as opted in, and explicit opt-out is the documented action.
     */
    public boolean isChannelEnabled(long merchantId, String channel) {
        String sql =
                "SELECT enabled_flag FROM communication_message_preferences"
                        + " WHERE merchant_id=:tenant_merchant_id AND channel=:channel LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        List<String> rows =
                jdbcTemplate.query(
                        sql,
                        TenantScopeGuard.scope(null, merchantId)
                                .addValue("channel", normalize(channel)),
                        (rs, rowNum) -> rs.getString("enabled_flag"));
        return rows.isEmpty() || "Y".equalsIgnoreCase(rows.get(0));
    }

    /**
     * Saves (or clears) a merchant's preference for a channel. A null/blank enabled value is
     * treated as Y. Returns the persisted row.
     */
    public PreferenceRow save(
            long merchantId,
            String channel,
            Boolean enabled,
            String quietHoursStart,
            String quietHoursEnd,
            String updatedBy) {
        String key = normalize(channel);
        boolean enabledFlag = enabled == null || enabled;
        String sql =
                "INSERT INTO communication_message_preferences (merchant_id, channel, enabled_flag,"
                        + " quiet_hours_start, quiet_hours_end, updated_by) VALUES"
                        + " (:tenant_merchant_id, :channel, :enabled_flag, :quiet_hours_start,"
                        + " :quiet_hours_end, :updated_by) ON DUPLICATE KEY UPDATE"
                        + " enabled_flag=VALUES(enabled_flag), quiet_hours_start=VALUES(quiet_hours_start),"
                        + " quiet_hours_end=VALUES(quiet_hours_end), updated_by=VALUES(updated_by)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("channel", key);
        p.addValue("enabled_flag", enabledFlag ? "Y" : "N");
        p.addValue("quiet_hours_start", normalizeTime(quietHoursStart));
        p.addValue("quiet_hours_end", normalizeTime(quietHoursEnd));
        p.addValue("updated_by", updatedBy == null ? "system" : updatedBy.trim());
        jdbcTemplate.update(sql, p);
        return find(merchantId, key)
                .orElseThrow(() -> new IllegalStateException("Failed to persist preference"));
    }

    public Optional<PreferenceRow> find(long merchantId, String channel) {
        String sql =
                "SELECT id, merchant_id, channel, enabled_flag, quiet_hours_start, quiet_hours_end,"
                        + " updated_by, created_at, updated_at FROM communication_message_preferences"
                        + " WHERE merchant_id=:tenant_merchant_id AND channel=:channel LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("channel", normalize(channel));
        List<PreferenceRow> rows = jdbcTemplate.query(sql, p, this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private String normalize(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new PaymentGatewayException("channel is required");
        }
        return channel.trim().toUpperCase();
    }

    private String normalizeTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        // Enforce a real clock time: hour 00-23, minute 00-59.
        if (!trimmed.matches("([01]?\\d|2[0-3]):[0-5]\\d")) {
            throw new PaymentGatewayException("quiet hours must be HH:mm, got: " + value);
        }
        return trimmed;
    }

    private PreferenceRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new PreferenceRow(
                rs.getLong("id"),
                rs.getLong("merchant_id"),
                rs.getString("channel"),
                "Y".equalsIgnoreCase(rs.getString("enabled_flag")),
                rs.getString("quiet_hours_start"),
                rs.getString("quiet_hours_end"),
                rs.getString("updated_by"),
                rs.getString("created_at"),
                rs.getString("updated_at"));
    }

    public record PreferenceRow(
            long id,
            long merchantId,
            String channel,
            boolean enabled,
            String quietHoursStart,
            String quietHoursEnd,
            String updatedBy,
            String createdAt,
            String updatedAt) {}
}
