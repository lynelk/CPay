package net.citotech.cito.admin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
        int providerSandboxRuns = count("provider_sandbox_runs");
        int statementValidationRuns = count("provider_statement_validation_runs");
        int callbackSecrets = count("merchant_callback_secrets");
        int openAlerts = scalar("SELECT COUNT(*) FROM operations_alerts WHERE alert_status='OPEN'");
        int parkedCallbacks = scalar("SELECT COUNT(*) FROM callback_tasks WHERE task_status='PARKED'");
        int dailyCloses = count("reconciliation_daily_closes");
        int adminAuditEvents = count("admin_audit_events");

        result.put("providerSandboxRuns", providerSandboxRuns);
        result.put("statementValidationRuns", statementValidationRuns);
        result.put("callbackSecrets", callbackSecrets);
        result.put("openAlerts", openAlerts);
        result.put("parkedCallbacks", parkedCallbacks);
        result.put("dailyCloses", dailyCloses);
        result.put("adminAuditEvents", adminAuditEvents);
        result.put("checklist", readinessChecklist(
                providerSandboxRuns,
                statementValidationRuns,
                callbackSecrets,
                openAlerts,
                parkedCallbacks,
                dailyCloses,
                adminAuditEvents));
        return result;
    }

    private List<Map<String, Object>> readinessChecklist(
            int providerSandboxRuns,
            int statementValidationRuns,
            int callbackSecrets,
            int openAlerts,
            int parkedCallbacks,
            int dailyCloses,
            int adminAuditEvents) {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(check("provider_sandbox", "Provider sandbox test completed", providerSandboxRuns > 0,
                providerSandboxRuns, "Run at least one provider sandbox test before go-live."));
        checks.add(check("statement_validation", "Statement validation run completed", statementValidationRuns > 0,
                statementValidationRuns, "Upload and validate a provider statement."));
        checks.add(check("callback_secrets", "Callback signing secret configured", callbackSecrets > 0,
                callbackSecrets, "Configure callback signing for at least one merchant."));
        checks.add(check("operations_alerts", "No open operations alerts", openAlerts == 0,
                openAlerts, "Clear open operations alerts before go-live."));
        checks.add(check("parked_callbacks", "No parked callbacks", parkedCallbacks == 0,
                parkedCallbacks, "Review or requeue parked callback deliveries."));
        checks.add(check("daily_close", "Daily close has run", dailyCloses > 0,
                dailyCloses, "Run the reconciliation daily close."));
        checks.add(check("admin_audit", "Admin audit is recording events", adminAuditEvents > 0,
                adminAuditEvents, "Perform an admin action and confirm audit capture."));
        return checks;
    }

    private Map<String, Object> check(String id, String label, boolean passing, int value, String action) {
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("id", id);
        check.put("label", label);
        check.put("status", passing ? "READY" : "ACTION_REQUIRED");
        check.put("value", value);
        check.put("action", passing ? "" : action);
        return check;
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

