package net.citotech.cito.admin;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FeatureFlagService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FeatureFlagService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list() {
        String sql = "SELECT flag_key, enabled, description, updated_at "
                + "FROM feature_flags ORDER BY flag_key ASC";
        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
    }

    public boolean isEnabled(String flagKey) {
        String sql = "SELECT enabled FROM feature_flags WHERE flag_key=:flag_key";
        try {
            Integer value = jdbcTemplate.queryForObject(
                    sql,
                    new MapSqlParameterSource("flag_key", normalize(flagKey)),
                    Integer.class);
            return value != null && value == 1;
        } catch (Exception exception) {
            return false;
        }
    }

    public Map<String, Object> save(String flagKey, boolean enabled, String description) {
        String normalizedKey = normalize(flagKey);
        String sql = "INSERT INTO feature_flags (flag_key, enabled, description) "
                + "VALUES (:flag_key, :enabled, :description) "
                + "ON DUPLICATE KEY UPDATE enabled=:enabled, description=:description";
        MapSqlParameterSource parameters = new MapSqlParameterSource();
        parameters.addValue("flag_key", normalizedKey);
        parameters.addValue("enabled", enabled ? 1 : 0);
        parameters.addValue("description", description == null ? "" : description.trim());
        jdbcTemplate.update(sql, parameters);
        return Map.of(
                "flagKey", normalizedKey,
                "enabled", enabled,
                "description", parameters.getValue("description"));
    }

    private String normalize(String flagKey) {
        if (flagKey == null || flagKey.isBlank()) {
            throw new IllegalArgumentException("Feature flag key is required.");
        }
        return flagKey.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
    }
}
