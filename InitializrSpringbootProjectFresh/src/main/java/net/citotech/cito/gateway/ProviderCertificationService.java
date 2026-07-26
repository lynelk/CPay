package net.citotech.cito.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProviderCertificationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ProviderCertificationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordSandboxEvidence(
            String providerCode,
            String channelCode,
            String scenarioName,
            long runId,
            String runStatus,
            String evidenceSummary) {
        MapSqlParameterSource p = baseParams(providerCode, channelCode, scenarioName);
        p.addValue("evidence_type", "SANDBOX_RUN");
        p.addValue("run_id", runId);
        p.addValue("statement_run_id", null);
        p.addValue("evidence_status", "PASSED".equalsIgnoreCase(runStatus) ? "CAPTURED" : "FAILED");
        p.addValue("summary", evidenceSummary);
        p.addValue("storage_ref", null);
        jdbcTemplate.update(
            "INSERT INTO provider_certification_evidence "
                + "(provider_code, channel_code, scenario_name, evidence_type, run_id, statement_run_id, evidence_status, evidence_summary, storage_ref) "
                + "VALUES (:provider_code, :channel_code, :scenario_name, :evidence_type, :run_id, :statement_run_id, :evidence_status, :summary, :storage_ref)",
            p);
    }

    public int recordStatementEvidence(
            String providerCode,
            String channelCode,
            String scenarioName,
            long statementRunId,
            String evidenceStatus,
            String evidenceSummary,
            String storageRef) {
        MapSqlParameterSource p = baseParams(providerCode, channelCode, scenarioName);
        p.addValue("evidence_type", "STATEMENT_VALIDATION");
        p.addValue("run_id", null);
        p.addValue("statement_run_id", statementRunId);
        p.addValue("evidence_status", normalized(evidenceStatus, "CAPTURED"));
        p.addValue("summary", evidenceSummary);
        p.addValue("storage_ref", storageRef);
        return jdbcTemplate.update(
            "INSERT INTO provider_certification_evidence "
                + "(provider_code, channel_code, scenario_name, evidence_type, run_id, statement_run_id, evidence_status, evidence_summary, storage_ref) "
                + "VALUES (:provider_code, :channel_code, :scenario_name, :evidence_type, :run_id, :statement_run_id, :evidence_status, :summary, :storage_ref)",
            p);
    }

    public int approveEvidence(long id, String approvedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        p.addValue("approved_by", blank(approvedBy) ? "system" : approvedBy.trim());
        return jdbcTemplate.update(
            "UPDATE provider_certification_evidence SET evidence_status='APPROVED', "
                + "approved_by=:approved_by, approved_at=CURRENT_TIMESTAMP "
                + "WHERE id=:id AND evidence_status='CAPTURED'",
            p);
    }

    public List<Map<String, Object>> listEvidence(String providerCode, String channelCode) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", blank(providerCode) ? null : providerCode.trim());
        p.addValue("channel_code", blank(channelCode) ? null : channelCode.trim());
        return jdbcTemplate.queryForList(
            "SELECT id, provider_code, channel_code, scenario_name, evidence_type, run_id, "
                + "statement_run_id, evidence_status, evidence_summary, storage_ref, approved_by, "
                + "approved_at, created_at, updated_at "
                + "FROM provider_certification_evidence "
                + "WHERE (:provider_code IS NULL OR provider_code=:provider_code) "
                + "AND (:channel_code IS NULL OR channel_code=:channel_code) "
                + "ORDER BY created_at DESC LIMIT 500",
            p);
    }

    public Map<String, Object> summary() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("evidenceByStatus", jdbcTemplate.queryForList(
            "SELECT evidence_status, COUNT(*) AS count FROM provider_certification_evidence GROUP BY evidence_status",
            new MapSqlParameterSource()));
        result.put("coverage", jdbcTemplate.queryForList(
            "SELECT r.provider_code, r.channel_code, r.scenario_name, "
                + "MAX(e.created_at) AS latest_evidence_at, "
                + "COALESCE(MAX(CASE WHEN e.evidence_status='APPROVED' THEN 1 ELSE 0 END), 0) AS approved "
                + "FROM provider_certification_requirements r "
                + "LEFT JOIN provider_certification_evidence e "
                + "ON (e.scenario_name=r.scenario_name AND (r.provider_code='*' OR r.provider_code=e.provider_code) "
                + "AND (r.channel_code='*' OR r.channel_code=e.channel_code)) "
                + "WHERE r.required_flag='YES' "
                + "GROUP BY r.provider_code, r.channel_code, r.scenario_name "
                + "ORDER BY r.provider_code, r.channel_code, r.scenario_name",
            new MapSqlParameterSource()));
        return result;
    }

    private MapSqlParameterSource baseParams(String providerCode, String channelCode, String scenarioName) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", normalized(providerCode, "UNKNOWN"));
        p.addValue("channel_code", normalized(channelCode, "UNKNOWN"));
        p.addValue("scenario_name", normalized(scenarioName, "manual_review").toLowerCase());
        return p;
    }

    private String normalized(String value, String fallback) {
        return blank(value) ? fallback : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
