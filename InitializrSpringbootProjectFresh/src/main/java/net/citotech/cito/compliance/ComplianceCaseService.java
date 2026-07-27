package net.citotech.cito.compliance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ComplianceCaseService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ComplianceCaseService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void captureRiskDecision(
            long merchantId,
            String requestReference,
            String direction,
            BigDecimal amount,
            String currency,
            RiskDecision decision) {
        recordShadowScore(merchantId, requestReference, direction, amount, currency, decision);
        if (!"ALLOW".equalsIgnoreCase(decision.getDecision())) {
            openRiskCase(merchantId, requestReference, decision);
        }
    }

    public List<Map<String, Object>> listOpenCases() {
        return jdbcTemplate.queryForList(
            "SELECT id, case_reference, case_type, entity_type, entity_id, source_reference, "
                + "severity, case_status, assigned_to, decision, decision_reason, created_at, updated_at "
                + "FROM compliance_cases WHERE case_status IN ('OPEN','IN_REVIEW') "
                + "ORDER BY FIELD(severity, 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW'), created_at DESC",
            new MapSqlParameterSource());
    }

    public int decideCase(long id, String decision, String reason, String actor) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", id);
        p.addValue("decision", normalized(decision, "REVIEWED"));
        p.addValue("reason", blank(reason) ? "Reviewed" : reason.trim());
        p.addValue("actor", blank(actor) ? "system" : actor.trim());
        int updated = jdbcTemplate.update(
            "UPDATE compliance_cases SET case_status='CLOSED', decision=:decision, "
                + "decision_reason=:reason, assigned_to=:actor, closed_at=CURRENT_TIMESTAMP "
                + "WHERE id=:id",
            p);
        if (updated > 0) {
            jdbcTemplate.update(
                "INSERT INTO compliance_case_notes (case_id, note_type, note_text, created_by) "
                    + "VALUES (:id, 'DECISION', :reason, :actor)",
                p);
        }
        return updated;
    }

    public int upsertProfile(
            String entityType,
            long entityId,
            String profileType,
            String tier,
            String status,
            String riskRating,
            String requiredDocumentsJson,
            String decisionReason,
            String verifiedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("entity_type", normalized(entityType, "MERCHANT"));
        p.addValue("entity_id", entityId);
        p.addValue("profile_type", normalized(profileType, "KYC"));
        p.addValue("tier", normalized(tier, "STANDARD"));
        p.addValue("status", normalized(status, "PENDING"));
        p.addValue("risk_rating", normalized(riskRating, "UNKNOWN"));
        p.addValue("required_documents_json", requiredDocumentsJson);
        p.addValue("decision_reason", decisionReason);
        p.addValue("verified_by", blank(verifiedBy) ? null : verifiedBy.trim());
        return jdbcTemplate.update(
            "INSERT INTO compliance_profiles "
                + "(entity_type, entity_id, profile_type, tier, status, risk_rating, required_documents_json, decision_reason, verified_by, verified_at) "
                + "VALUES (:entity_type, :entity_id, :profile_type, :tier, :status, :risk_rating, :required_documents_json, :decision_reason, :verified_by, "
                + "CASE WHEN :verified_by IS NULL THEN NULL ELSE CURRENT_TIMESTAMP END) "
                + "ON DUPLICATE KEY UPDATE tier=VALUES(tier), status=VALUES(status), risk_rating=VALUES(risk_rating), "
                + "required_documents_json=VALUES(required_documents_json), decision_reason=VALUES(decision_reason), "
                + "verified_by=VALUES(verified_by), verified_at=VALUES(verified_at)",
            p);
    }

    public List<Map<String, Object>> listProfiles(String status) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("status", blank(status) ? null : normalized(status, ""));
        return jdbcTemplate.queryForList(
            "SELECT id, entity_type, entity_id, profile_type, tier, status, risk_rating, decision_reason, "
                + "verified_by, verified_at, expires_at, created_at, updated_at "
                + "FROM compliance_profiles WHERE (:status IS NULL OR status=:status) "
                + "ORDER BY updated_at DESC LIMIT 500",
            p);
    }

    private void openRiskCase(long merchantId, String requestReference, RiskDecision decision) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("case_reference", riskCaseReference(merchantId, requestReference, decision.getReasonCode()));
        p.addValue("case_type", "RISK_DECISION");
        p.addValue("entity_type", "MERCHANT");
        p.addValue("entity_id", merchantId);
        p.addValue("source_reference", requestReference);
        p.addValue("severity", "BLOCK".equalsIgnoreCase(decision.getDecision()) ? "HIGH" : "MEDIUM");
        p.addValue("decision", decision.getDecision());
        p.addValue("reason", decision.getReasonCode() + ": " + decision.getSummary());
        jdbcTemplate.update(
            "INSERT INTO compliance_cases "
                + "(case_reference, case_type, entity_type, entity_id, source_reference, severity, decision, decision_reason) "
                + "VALUES (:case_reference, :case_type, :entity_type, :entity_id, :source_reference, :severity, :decision, :reason) "
                + "ON DUPLICATE KEY UPDATE severity=VALUES(severity), decision=VALUES(decision), "
                + "decision_reason=VALUES(decision_reason), "
                + "case_status=CASE WHEN case_status='CLOSED' THEN case_status ELSE 'OPEN' END",
            p);
    }

    private void recordShadowScore(
            long merchantId,
            String requestReference,
            String direction,
            BigDecimal amount,
            String currency,
            RiskDecision decision) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("reference", requestReference);
        p.addValue("score_type", "RULE_ENGINE");
        p.addValue("score_value", scoreValue(decision));
        p.addValue("features_json", "{\"direction\":\"" + json(direction) + "\",\"currency\":\""
            + json(currency) + "\",\"amount\":\"" + amount + "\",\"decision\":\""
            + json(decision.getDecision()) + "\",\"reasonCode\":\"" + json(decision.getReasonCode()) + "\"}");
        jdbcTemplate.update(
            "INSERT INTO risk_decision_scores "
                + "(merchant_id, request_reference, score_type, score_value, features_json, score_status) "
                + "VALUES (:merchant_id, :reference, :score_type, :score_value, :features_json, 'SHADOW')",
            p);
    }

    private BigDecimal scoreValue(RiskDecision decision) {
        if ("BLOCK".equalsIgnoreCase(decision.getDecision())) {
            return new BigDecimal("1.0000");
        }
        if ("REVIEW".equalsIgnoreCase(decision.getDecision())) {
            return new BigDecimal("0.6500");
        }
        return new BigDecimal("0.0500");
    }

    private String riskCaseReference(long merchantId, String requestReference, String reasonCode) {
        String seed = merchantId + ":" + nullToEmpty(requestReference) + ":" + nullToEmpty(reasonCode);
        return "RISK-" + merchantId + "-" + CanonicalRequestSigner.sha256Hex(seed).substring(0, 16);
    }

    private String normalized(String value, String fallback) {
        return blank(value) ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String json(String value) {
        return nullToEmpty(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
