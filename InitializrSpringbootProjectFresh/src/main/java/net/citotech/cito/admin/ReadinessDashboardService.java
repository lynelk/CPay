package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ReadinessDashboardService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ReadinessDashboardService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerSandboxRuns", count("provider_sandbox_runs"));
        result.put("statementValidationRuns", count("provider_statement_validation_runs"));
        result.put("callbackSecrets", count("merchant_callback_secrets"));
        result.put("openAlerts", scalar("SELECT COUNT(*) FROM operations_alerts WHERE alert_status='OPEN'"));
        result.put("parkedCallbacks", scalar("SELECT COUNT(*) FROM callback_tasks WHERE task_status='PARKED'"));
        result.put("dailyCloses", count("reconciliation_daily_closes"));
        result.put("adminAuditEvents", count("admin_audit_events"));
        return result;
    }

    private Integer count(String table) {
        return scalar("SELECT COUNT(*) FROM " + table);
    }

    private Integer scalar(String sql) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }
}

