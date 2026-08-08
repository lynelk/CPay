package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Feature registry (ADR 0002 "@Owner of the V35 baseline contract"): the single place that decides
 * whether a feature is enabled, combining the global {@code feature_flags} toggle (V5) with the
 * per-merchant override in {@code merchant_feature_flags} (V36).
 *
 * <p>Resolution order: a per-merchant row, when present, wins over the global default; otherwise
 * the global flag applies. Merchant-scoped reads and writes always pass through {@link
 * TenantScopeGuard} so one merchant can never read or alter another merchant's overrides.
 */
@Service
public class FeatureRegistryService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeatureRegistryService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** The global {@code feature_flags} catalog (registry defaults). */
    public List<Map<String, Object>> listGlobal() {
        return jdbcTemplate.queryForList(
                "SELECT flag_key, enabled, description, updated_at "
                        + "FROM feature_flags ORDER BY flag_key ASC",
                new MapSqlParameterSource());
    }

    /** Per-merchant overrides for {@code merchantId}, tenant-scoped. */
    public List<Map<String, Object>> listMerchant(long merchantId) {
        String sql =
                "SELECT id, merchant_id, flag_key, enabled, description, updated_at "
                        + "FROM merchant_feature_flags WHERE merchant_id=:tenant_merchant_id "
                        + "ORDER BY flag_key ASC";
        TenantScopeGuard.assertTenantBound(sql);
        return jdbcTemplate.queryForList(sql, TenantScopeGuard.scope(null, merchantId));
    }

    /**
     * All features with their effective state for {@code merchantId} (global default + override).
     */
    public List<Map<String, Object>> listEffective(long merchantId) {
        Map<String, Map<String, Object>> byKey = new LinkedHashMap<>();
        for (Map<String, Object> global : listGlobal()) {
            String key = String.valueOf(global.get("flag_key"));
            Map<String, Object> view = new LinkedHashMap<>(global);
            view.put("merchantOverridePresent", false);
            view.put("merchantOverrideEnabled", null);
            byKey.put(key, view);
        }
        for (Map<String, Object> override : listMerchant(merchantId)) {
            String key = String.valueOf(override.get("flag_key"));
            Map<String, Object> view = byKey.get(key);
            if (view == null) {
                view = new LinkedHashMap<>();
                view.put("flag_key", key);
                view.put("description", String.valueOf(override.getOrDefault("description", "")));
                view.put("updated_at", override.get("updated_at"));
                view.put("merchantOverridePresent", true);
                view.put("merchantOverrideEnabled", toBool(override.get("enabled")));
                view.put("enabled", toBool(override.get("enabled")));
                byKey.put(key, view);
                continue;
            }
            view.put("merchantOverridePresent", true);
            boolean overrideEnabled = toBool(override.get("enabled"));
            view.put("merchantOverrideEnabled", overrideEnabled);
            view.put("enabled", overrideEnabled);
            view.put("updated_at", override.get("updated_at"));
        }
        return List.copyOf(byKey.values());
    }

    /**
     * Effective feature state for {@code merchantId}: a per-merchant override wins when present,
     * otherwise the global default.
     */
    public boolean isEnabled(String flagKey, long merchantId) {
        String normalized = normalize(flagKey);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("flag_key", normalized);
        p.addValue("tenant_merchant_id", merchantId);
        List<Integer> merchantOverride =
                jdbcTemplate.query(
                        "SELECT enabled FROM merchant_feature_flags "
                                + "WHERE merchant_id=:tenant_merchant_id AND flag_key=:flag_key "
                                + "LIMIT 1",
                        p,
                        (rs, rowNum) -> rs.getInt("enabled"));
        if (!merchantOverride.isEmpty()) {
            return merchantOverride.get(0) == 1;
        }
        List<Integer> global =
                jdbcTemplate.query(
                        "SELECT enabled FROM feature_flags WHERE flag_key=:flag_key LIMIT 1",
                        new MapSqlParameterSource("flag_key", normalized),
                        (rs, rowNum) -> rs.getInt("enabled"));
        return !global.isEmpty() && global.get(0) == 1;
    }

    /** Global feature state (no merchant context). */
    public boolean isGloballyEnabled(String flagKey) {
        String normalized = normalize(flagKey);
        List<Integer> rows =
                jdbcTemplate.query(
                        "SELECT enabled FROM feature_flags WHERE flag_key=:flag_key LIMIT 1",
                        new MapSqlParameterSource("flag_key", normalized),
                        (rs, rowNum) -> rs.getInt("enabled"));
        return !rows.isEmpty() && rows.get(0) == 1;
    }

    /** Sets (or replaces) a per-merchant override, tenant-scoped. Returns the saved view. */
    public Map<String, Object> setMerchantOverride(
            long merchantId, String flagKey, boolean enabled, String description) {
        String key = normalize(flagKey);
        String sql =
                "INSERT INTO merchant_feature_flags (merchant_id, flag_key, enabled, description) "
                        + "VALUES (:tenant_merchant_id, :flag_key, :enabled, :description) "
                        + "ON DUPLICATE KEY UPDATE enabled=VALUES(enabled), description=VALUES(description)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("flag_key", key);
        p.addValue("enabled", enabled ? 1 : 0);
        p.addValue("description", description == null ? "" : description.trim());
        jdbcTemplate.update(sql, p);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("merchantId", merchantId);
        view.put("flagKey", key);
        view.put("enabled", enabled);
        view.put("description", description == null ? "" : description.trim());
        return view;
    }

    /** Deletes a per-merchant override (reverts to the global default). Returns rows deleted. */
    public int removeMerchantOverride(long merchantId, String flagKey) {
        String key = normalize(flagKey);
        String sql =
                "DELETE FROM merchant_feature_flags "
                        + "WHERE merchant_id=:tenant_merchant_id AND flag_key=:flag_key";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("flag_key", key);
        return jdbcTemplate.update(sql, p);
    }

    private String normalize(String flagKey) {
        if (flagKey == null || flagKey.isBlank()) {
            throw new IllegalArgumentException("Feature flag key is required.");
        }
        return flagKey.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }

    private boolean toBool(Object value) {
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return "1".equals(String.valueOf(value)) || "true".equalsIgnoreCase(String.valueOf(value));
    }
}
