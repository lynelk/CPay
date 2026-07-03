package net.citotech.cito.admin;

import java.util.HashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class OpsAlertService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public OpsAlertService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long open(String type, String severity, String message, String resource) {
        String sql = "INSERT INTO operations_alerts (alert_type, severity, message, resource_reference) VALUES (:type, :severity, :message, :resource)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("type", type);
        p.addValue("severity", severity);
        p.addValue("message", message);
        p.addValue("resource", resource);
        jdbcTemplate.update(sql, p);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
    }

    public int resolve(long id) {
        return jdbcTemplate.update("UPDATE operations_alerts SET alert_status='RESOLVED', resolved_at=CURRENT_TIMESTAMP WHERE id=:id", new MapSqlParameterSource("id", id));
    }

    public Map<String, Object> dashboard() {
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("openAlerts", count("OPEN"));
        dashboard.put("callbackParked", scalar("SELECT COUNT(*) FROM callback_tasks WHERE task_status='PARKED'"));
        dashboard.put("reconUnmatched", scalar("SELECT COUNT(*) FROM reconciliation_records WHERE match_status='UNMATCHED'"));
        dashboard.put("statementFailures", scalar("SELECT COUNT(*) FROM provider_statement_validation_runs WHERE validation_status='FAILED'"));
        return dashboard;
    }

    private Integer count(String status) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operations_alerts WHERE alert_status=:status", new MapSqlParameterSource("status", status), Integer.class);
    }

    private Integer scalar(String sql) {
        return jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
    }
}
