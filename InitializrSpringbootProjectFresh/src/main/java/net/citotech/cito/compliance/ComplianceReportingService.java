package net.citotech.cito.compliance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ComplianceReportingService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ComplianceReportingService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("openControlEvents", scalar("SELECT COUNT(*) FROM operating_control_events WHERE event_status='OPEN'"));
        result.put("highSeverityControlEvents", scalar("SELECT COUNT(*) FROM operating_control_events WHERE event_status='OPEN' AND severity='HIGH'"));
        result.put("providerEndpointRuns", scalar("SELECT COUNT(*) FROM provider_endpoint_runs"));
        result.put("failedProviderEndpointRuns", scalar("SELECT COUNT(*) FROM provider_endpoint_runs WHERE run_status='FAILED'"));
        result.put("parkedCallbacks", scalar("SELECT COUNT(*) FROM callback_tasks WHERE task_status='PARKED'"));
        result.put("pendingMerchantChannels", scalar("SELECT COUNT(*) FROM merchant_channel_credentials WHERE status='SUBMITTED_FOR_APPROVAL'"));
        result.put("activeMerchantChannels", scalar("SELECT COUNT(*) FROM merchant_channel_credentials WHERE status='ACTIVE'"));
        result.put("openDailyCloses", scalar("SELECT COUNT(*) FROM reconciliation_daily_closes WHERE close_status='OPEN'"));
        result.put("closedDailyCloses", scalar("SELECT COUNT(*) FROM reconciliation_daily_closes WHERE close_status='CLOSED'"));
        result.put("openComplianceCases", scalar("SELECT COUNT(*) FROM compliance_cases WHERE case_status IN ('OPEN','IN_REVIEW')"));
        result.put("highSeverityComplianceCases", scalar("SELECT COUNT(*) FROM compliance_cases WHERE case_status IN ('OPEN','IN_REVIEW') AND severity IN ('CRITICAL','HIGH')"));
        result.put("pendingComplianceProfiles", scalar("SELECT COUNT(*) FROM compliance_profiles WHERE status IN ('PENDING','IN_REVIEW')"));
        result.put("approvedProviderEvidence", scalar("SELECT COUNT(*) FROM provider_certification_evidence WHERE evidence_status='APPROVED'"));
        result.put("capturedProviderEvidence", scalar("SELECT COUNT(*) FROM provider_certification_evidence WHERE evidence_status='CAPTURED'"));
        return result;
    }

    public Map<String, Object> report(String fromDate, String toDate) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("from_date", blank(fromDate) ? "1970-01-01" : fromDate);
        p.addValue("to_date", blank(toDate) ? "2999-12-31" : toDate);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("period", Map.of("from", p.getValue("from_date"), "to", p.getValue("to_date")));
        report.put("controlEvents", rows("SELECT event_type, severity, event_status, reference_value, message, created_at, reviewed_by, reviewed_at FROM operating_control_events WHERE DATE(created_at) BETWEEN :from_date AND :to_date ORDER BY created_at DESC LIMIT 500", p));
        report.put("providerRuns", rows("SELECT channel_code, operation_name, reference_value, http_status, run_status, created_at FROM provider_endpoint_runs WHERE DATE(created_at) BETWEEN :from_date AND :to_date ORDER BY created_at DESC LIMIT 500", p));
        report.put("complianceCases", rows("SELECT case_reference, case_type, entity_type, entity_id, source_reference, severity, case_status, decision, decision_reason, created_at, closed_at FROM compliance_cases WHERE DATE(created_at) BETWEEN :from_date AND :to_date ORDER BY created_at DESC LIMIT 500", p));
        report.put("providerCertificationEvidence", rows("SELECT provider_code, channel_code, scenario_name, evidence_type, evidence_status, evidence_summary, approved_by, approved_at, created_at FROM provider_certification_evidence WHERE DATE(created_at) BETWEEN :from_date AND :to_date ORDER BY created_at DESC LIMIT 500", p));
        report.put("callbackExceptions", rows("SELECT merchant_id, transaction_id, reference_value, task_status, attempt_count, message FROM callback_tasks WHERE task_status IN ('PARKED','RETRY') ORDER BY id DESC LIMIT 500", p));
        report.put("dailyCloses", rows("SELECT close_date, currency, close_status, matched_count, unmatched_count, exception_count, variance_amount, closed_by, closed_at FROM reconciliation_daily_closes WHERE close_date BETWEEN :from_date AND :to_date ORDER BY close_date DESC LIMIT 500", p));
        return report;
    }

    public int markReviewed(long id, String reviewedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        p.addValue("reviewed_by", blank(reviewedBy) ? "system" : reviewedBy);
        return jdbcTemplate.update("UPDATE operating_control_events SET event_status='REVIEWED', reviewed_by=:reviewed_by, reviewed_at=CURRENT_TIMESTAMP WHERE id=:id", p);
    }

    private Integer scalar(String sql) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    private List<Map<String, Object>> rows(String sql, MapSqlParameterSource p) {
        return jdbcTemplate.queryForList(sql, p);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}

