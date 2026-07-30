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
        int parkedCallbacks =
                scalar("SELECT COUNT(*) FROM callback_tasks WHERE task_status='PARKED'");
        int dailyCloses = count("reconciliation_daily_closes");
        int adminAuditEvents = count("admin_audit_events");
        int openComplianceCases =
                scalar(
                        "SELECT COUNT(*) FROM compliance_cases WHERE case_status IN ('OPEN','IN_REVIEW')");
        int approvedProviderEvidence =
                scalar(
                        "SELECT COUNT(*) FROM provider_certification_evidence WHERE evidence_status='APPROVED'");
        int pendingComplianceProfiles =
                scalar(
                        "SELECT COUNT(*) FROM compliance_profiles WHERE status IN ('PENDING','IN_REVIEW')");

        result.put("providerSandboxRuns", providerSandboxRuns);
        result.put("statementValidationRuns", statementValidationRuns);
        result.put("callbackSecrets", callbackSecrets);
        result.put("openAlerts", openAlerts);
        result.put("parkedCallbacks", parkedCallbacks);
        result.put("dailyCloses", dailyCloses);
        result.put("adminAuditEvents", adminAuditEvents);
        result.put("openComplianceCases", openComplianceCases);
        result.put("approvedProviderEvidence", approvedProviderEvidence);
        result.put("pendingComplianceProfiles", pendingComplianceProfiles);
        result.put(
                "checklist",
                readinessChecklist(
                        providerSandboxRuns,
                        statementValidationRuns,
                        callbackSecrets,
                        openAlerts,
                        parkedCallbacks,
                        dailyCloses,
                        adminAuditEvents,
                        openComplianceCases,
                        approvedProviderEvidence,
                        pendingComplianceProfiles));
        return result;
    }

    private List<Map<String, Object>> readinessChecklist(
            int providerSandboxRuns,
            int statementValidationRuns,
            int callbackSecrets,
            int openAlerts,
            int parkedCallbacks,
            int dailyCloses,
            int adminAuditEvents,
            int openComplianceCases,
            int approvedProviderEvidence,
            int pendingComplianceProfiles) {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(
                check(
                        "provider_sandbox",
                        "Provider sandbox test completed",
                        providerSandboxRuns > 0,
                        providerSandboxRuns,
                        "Run at least one provider sandbox test before go-live."));
        checks.add(
                check(
                        "statement_validation",
                        "Statement validation run completed",
                        statementValidationRuns > 0,
                        statementValidationRuns,
                        "Upload and validate a provider statement."));
        checks.add(
                check(
                        "callback_secrets",
                        "Callback signing secret configured",
                        callbackSecrets > 0,
                        callbackSecrets,
                        "Configure callback signing for at least one merchant."));
        checks.add(
                check(
                        "operations_alerts",
                        "No open operations alerts",
                        openAlerts == 0,
                        openAlerts,
                        "Clear open operations alerts before go-live."));
        checks.add(
                check(
                        "parked_callbacks",
                        "No parked callbacks",
                        parkedCallbacks == 0,
                        parkedCallbacks,
                        "Review or requeue parked callback deliveries."));
        checks.add(
                check(
                        "daily_close",
                        "Daily close has run",
                        dailyCloses > 0,
                        dailyCloses,
                        "Run the reconciliation daily close."));
        checks.add(
                check(
                        "admin_audit",
                        "Admin audit is recording events",
                        adminAuditEvents > 0,
                        adminAuditEvents,
                        "Perform an admin action and confirm audit capture."));
        checks.add(
                check(
                        "compliance_cases",
                        "No open compliance cases",
                        openComplianceCases == 0,
                        openComplianceCases,
                        "Review open compliance cases before go-live."));
        checks.add(
                check(
                        "provider_certification",
                        "Provider certification evidence approved",
                        approvedProviderEvidence > 0,
                        approvedProviderEvidence,
                        "Capture and approve provider certification evidence."));
        checks.add(
                check(
                        "compliance_profiles",
                        "No pending compliance profiles",
                        pendingComplianceProfiles == 0,
                        pendingComplianceProfiles,
                        "Complete pending KYC/compliance profiles."));
        return checks;
    }

    /**
     * Per-merchant go-live checklist (audit item O6). {@link #summary()} above is entirely
     * platform-wide with no merchant filter anywhere; this method scopes every check to a single
     * merchant instead:
     *
     * <ul>
     *   <li>{@code provider_sandbox_runs}, {@code provider_statement_validation_runs}, and {@code
     *       provider_certification_evidence} have no merchant reference at all (only {@code
     *       provider_code}/{@code channel_code}), so they are scoped indirectly: for each channel
     *       this merchant has actually configured (via {@code
     *       merchant_channel_credentials.merchant_id}), has that channel had a sandbox run /
     *       statement validation run / approved certification evidence.
     *   <li>{@code merchant_callback_secrets} has a direct {@code merchant_id} column and is
     *       filtered on it directly.
     *   <li>{@code compliance_profiles}/{@code compliance_cases} use the polymorphic {@code
     *       entity_type}/{@code entity_id} pair rather than a {@code merchant_id} column; {@code
     *       entity_type='MERCHANT'} is the real convention merchant-scoped rows are written with
     *       (see {@code ComplianceCaseService.upsertProfile}/{@code openRiskCase}), so that is the
     *       filter used here.
     * </ul>
     *
     * {@code operations_alerts}, {@code reconciliation_daily_closes}, and {@code
     * admin_audit_events} are deliberately NOT included: they are platform-wide operational
     * concepts with no merchant reference of any kind, and force-fitting them into a per-merchant
     * checklist would conflate "is the whole platform healthy" with "is this merchant ready to go
     * live." Ops should keep using {@link #summary()} for those.
     */
    public Map<String, Object> merchantSummary(long merchantId) {
        Map<String, Object> result = new LinkedHashMap<>();
        MapSqlParameterSource merchantParam = new MapSqlParameterSource("merchantId", merchantId);

        List<String> configuredChannelCodes =
                jdbcTemplate.queryForList(
                        "SELECT DISTINCT channel_code FROM merchant_channel_credentials WHERE merchant_id = :merchantId",
                        merchantParam,
                        String.class);
        int configuredChannels = configuredChannelCodes.size();
        int channelsWithSandboxRuns =
                coveredChannelCount(configuredChannelCodes, "provider_sandbox_runs", null);
        int channelsWithStatementValidation =
                coveredChannelCount(
                        configuredChannelCodes, "provider_statement_validation_runs", null);
        int channelsWithApprovedCertification =
                coveredChannelCount(
                        configuredChannelCodes,
                        "provider_certification_evidence",
                        "evidence_status='APPROVED'");

        int callbackSecrets =
                scalar(
                        "SELECT COUNT(*) FROM merchant_callback_secrets WHERE merchant_id=:merchantId AND active_flag='YES'",
                        merchantParam);
        int complianceProfiles =
                scalar(
                        "SELECT COUNT(*) FROM compliance_profiles WHERE entity_type='MERCHANT' AND entity_id=:merchantId",
                        merchantParam);
        int pendingComplianceProfiles =
                scalar(
                        "SELECT COUNT(*) FROM compliance_profiles WHERE entity_type='MERCHANT' AND entity_id=:merchantId "
                                + "AND status IN ('PENDING','IN_REVIEW')",
                        merchantParam);
        int openComplianceCases =
                scalar(
                        "SELECT COUNT(*) FROM compliance_cases WHERE entity_type='MERCHANT' AND entity_id=:merchantId "
                                + "AND case_status IN ('OPEN','IN_REVIEW')",
                        merchantParam);

        result.put("merchantId", merchantId);
        result.put("configuredChannels", configuredChannels);
        result.put("channelsWithSandboxRuns", channelsWithSandboxRuns);
        result.put("channelsWithStatementValidation", channelsWithStatementValidation);
        result.put("channelsWithApprovedCertification", channelsWithApprovedCertification);
        result.put("callbackSecrets", callbackSecrets);
        result.put("complianceProfiles", complianceProfiles);
        result.put("pendingComplianceProfiles", pendingComplianceProfiles);
        result.put("openComplianceCases", openComplianceCases);
        result.put(
                "checklist",
                merchantReadinessChecklist(
                        configuredChannels,
                        channelsWithSandboxRuns,
                        channelsWithStatementValidation,
                        channelsWithApprovedCertification,
                        callbackSecrets,
                        complianceProfiles,
                        pendingComplianceProfiles,
                        openComplianceCases));
        return result;
    }

    private List<Map<String, Object>> merchantReadinessChecklist(
            int configuredChannels,
            int channelsWithSandboxRuns,
            int channelsWithStatementValidation,
            int channelsWithApprovedCertification,
            int callbackSecrets,
            int complianceProfiles,
            int pendingComplianceProfiles,
            int openComplianceCases) {
        List<Map<String, Object>> checks = new ArrayList<>();
        checks.add(
                check(
                        "channels_configured",
                        "At least one payment channel configured",
                        configuredChannels > 0,
                        configuredChannels,
                        "Configure at least one payment channel for this merchant."));
        checks.add(
                check(
                        "provider_sandbox",
                        "Provider sandbox test completed for every configured channel",
                        configuredChannels > 0 && channelsWithSandboxRuns == configuredChannels,
                        channelsWithSandboxRuns,
                        "Run a provider sandbox test for each of this merchant's configured channels."));
        checks.add(
                check(
                        "statement_validation",
                        "Statement validation run completed for every configured channel",
                        configuredChannels > 0
                                && channelsWithStatementValidation == configuredChannels,
                        channelsWithStatementValidation,
                        "Upload and validate a provider statement for each of this merchant's configured channels."));
        checks.add(
                check(
                        "provider_certification",
                        "Provider certification evidence approved for every configured channel",
                        configuredChannels > 0
                                && channelsWithApprovedCertification == configuredChannels,
                        channelsWithApprovedCertification,
                        "Capture and approve provider certification evidence for each of this merchant's configured channels."));
        checks.add(
                check(
                        "callback_secrets",
                        "Callback signing secret configured",
                        callbackSecrets > 0,
                        callbackSecrets,
                        "Configure a callback signing secret for this merchant."));
        checks.add(
                check(
                        "compliance_profile",
                        "Compliance profile completed",
                        complianceProfiles > 0 && pendingComplianceProfiles == 0,
                        pendingComplianceProfiles,
                        "Create and complete this merchant's compliance profile (KYC/AML review)."));
        checks.add(
                check(
                        "compliance_cases",
                        "No open compliance cases",
                        openComplianceCases == 0,
                        openComplianceCases,
                        "Review this merchant's open compliance cases before go-live."));
        return checks;
    }

    private int coveredChannelCount(
            List<String> channelCodes, String table, String extraCondition) {
        if (channelCodes.isEmpty()) {
            return 0;
        }
        String sql =
                "SELECT COUNT(DISTINCT channel_code) FROM "
                        + table
                        + " WHERE channel_code IN (:channelCodes)"
                        + (extraCondition == null ? "" : " AND " + extraCondition);
        MapSqlParameterSource params = new MapSqlParameterSource("channelCodes", channelCodes);
        return scalar(sql, params);
    }

    private Map<String, Object> check(
            String id, String label, boolean passing, int value, String action) {
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
        return scalar(sql, new MapSqlParameterSource());
    }

    private Integer scalar(String sql, MapSqlParameterSource params) {
        try {
            Integer value = jdbcTemplate.queryForObject(sql, params, Integer.class);
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }
}
