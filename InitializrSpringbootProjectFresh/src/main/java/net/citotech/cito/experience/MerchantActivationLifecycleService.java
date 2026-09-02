package net.citotech.cito.experience;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MerchantActivationLifecycleService {
    private static final List<Step> STEPS =
            List.of(
                    new Step(
                            "ACCOUNT_CREATED",
                            "Account created",
                            "MERCHANT",
                            "Confirm the account owner and primary contact."),
                    new Step(
                            "EMAIL_VERIFIED",
                            "Email verified",
                            "MERCHANT",
                            "Verify the primary account email."),
                    new Step(
                            "BUSINESS_PROFILE",
                            "Business profile completed",
                            "MERCHANT",
                            "Provide the legal business profile and operating details."),
                    new Step(
                            "OWNERSHIP",
                            "Ownership and directors recorded",
                            "MERCHANT",
                            "Record directors and beneficial owners."),
                    new Step(
                            "DOCUMENTS",
                            "Verification documents submitted",
                            "MERCHANT",
                            "Upload current, legible verification evidence."),
                    new Step(
                            "KYB_REVIEW",
                            "KYB review",
                            "COMPLIANCE",
                            "Compliance reviews the submitted business evidence."),
                    new Step(
                            "RISK_REVIEW",
                            "Risk review",
                            "RISK",
                            "Risk confirms the account risk profile and controls."),
                    new Step(
                            "COMMERCIAL_APPROVAL",
                            "Commercial approval",
                            "SALES",
                            "Commercial terms and products are approved."),
                    new Step(
                            "SERVICES_SELECTED",
                            "Services selected",
                            "MERCHANT",
                            "Select only the Cito services the business needs."),
                    new Step(
                            "SANDBOX_CONFIGURED",
                            "Sandbox configured",
                            "DEVELOPER",
                            "Create a sandbox application and credentials."),
                    new Step(
                            "INTEGRATION_TESTED",
                            "Integration tested",
                            "DEVELOPER",
                            "Complete applicable collection, payout and callback test journeys."),
                    new Step(
                            "PROVIDER_CERTIFIED",
                            "Provider certification completed",
                            "OPERATIONS",
                            "Attach provider and statement evidence for enabled channels."),
                    new Step(
                            "SETTLEMENT_CONFIGURED",
                            "Settlement configured",
                            "FINANCE",
                            "Confirm settlement accounts, schedule and currency."),
                    new Step(
                            "GO_LIVE_APPROVED",
                            "Go-live approved",
                            "OPERATIONS",
                            "Complete maker-checker readiness approval."),
                    new Step(
                            "PRODUCTION_ACTIVATED",
                            "Production activated",
                            "OPERATIONS",
                            "Activate production with monitored limits."));

    private final NamedParameterJdbcTemplate jdbc;

    public MerchantActivationLifecycleService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void ensure(long merchantId) {
        MapSqlParameterSource merchantParams = new MapSqlParameterSource("merchantId", merchantId);
        Integer merchantCount =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM merchants WHERE id=:merchantId",
                        merchantParams,
                        Integer.class);
        if (merchantCount == null || merchantCount == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant not found");
        }
        jdbc.update(
                """
                INSERT INTO merchant_activation_lifecycles
                    (lifecycle_reference,merchant_id,status,current_step_code,next_action)
                VALUES (:reference,:merchantId,'ACCOUNT_CREATED','ACCOUNT_CREATED','Complete the business profile.')
                ON DUPLICATE KEY UPDATE merchant_id=VALUES(merchant_id)
                """,
                merchantParams.addValue(
                        "reference", String.format(Locale.ROOT, "ACT-%012d", merchantId)));
        Long lifecycleId =
                jdbc.queryForObject(
                        "SELECT id FROM merchant_activation_lifecycles WHERE merchant_id=:merchantId",
                        merchantParams,
                        Long.class);
        if (lifecycleId == null) {
            throw new IllegalStateException("Merchant lifecycle could not be initialized");
        }
        for (int index = 0; index < STEPS.size(); index++) {
            Step step = STEPS.get(index);
            jdbc.update(
                    """
                    INSERT INTO merchant_activation_steps
                        (lifecycle_id,step_code,step_name,status,responsible_party,required_for_activation,guidance,sort_order)
                    VALUES (:lifecycleId,:stepCode,:stepName,:status,:responsibleParty,TRUE,:guidance,:sortOrder)
                    ON DUPLICATE KEY UPDATE step_name=VALUES(step_name), responsible_party=VALUES(responsible_party),
                        guidance=VALUES(guidance), sort_order=VALUES(sort_order)
                    """,
                    new MapSqlParameterSource("lifecycleId", lifecycleId)
                            .addValue("stepCode", step.code())
                            .addValue("stepName", step.name())
                            .addValue("status", index == 0 ? "COMPLETED" : "NOT_STARTED")
                            .addValue("responsibleParty", step.responsibleParty())
                            .addValue("guidance", step.guidance())
                            .addValue("sortOrder", index + 1));
        }
    }

    @Transactional
    public void updateStep(
            long merchantId,
            String stepCode,
            String status,
            String actor,
            String blocker,
            boolean administrator) {
        ensure(merchantId);
        String normalizedStep = stepCode.trim().toUpperCase(Locale.ROOT);
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if (!Set.of(
                        "NOT_STARTED",
                        "IN_PROGRESS",
                        "SUBMITTED",
                        "COMPLETED",
                        "BLOCKED",
                        "FAILED",
                        "NEEDS_RESUBMISSION",
                        "WAIVED",
                        "SKIPPED")
                .contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported lifecycle step status");
        }
        MapSqlParameterSource scope =
                new MapSqlParameterSource("merchantId", merchantId)
                        .addValue("stepCode", normalizedStep);
        List<Map<String, Object>> steps =
                jdbc.queryForList(
                        """
                        SELECT s.id,s.responsible_party FROM merchant_activation_steps s
                        JOIN merchant_activation_lifecycles l ON l.id=s.lifecycle_id
                        WHERE l.merchant_id=:merchantId AND s.step_code=:stepCode
                        """,
                        scope);
        if (steps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Lifecycle step not found");
        }
        String responsibleParty = String.valueOf(steps.get(0).get("responsible_party"));
        if (!administrator && !Set.of("MERCHANT", "DEVELOPER").contains(responsibleParty)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "This step must be decided by " + responsibleParty);
        }
        if (!administrator && Set.of("WAIVED", "SKIPPED").contains(normalizedStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only an administrator may waive or skip a lifecycle step");
        }
        jdbc.update(
                """
                UPDATE merchant_activation_steps
                SET status=:status,blocker=:blocker,completed_by=CASE WHEN :status IN ('COMPLETED','WAIVED','SKIPPED') THEN :actor ELSE NULL END,
                    completed_at=CASE WHEN :status IN ('COMPLETED','WAIVED','SKIPPED') THEN CURRENT_TIMESTAMP ELSE NULL END
                WHERE id=:stepId
                """,
                new MapSqlParameterSource("status", normalizedStatus)
                        .addValue("blocker", blocker)
                        .addValue("actor", actor)
                        .addValue("stepId", steps.get(0).get("id")));
        recompute(merchantId);
    }

    private void recompute(long merchantId) {
        MapSqlParameterSource params = new MapSqlParameterSource("merchantId", merchantId);
        List<Map<String, Object>> steps =
                jdbc.queryForList(
                        """
                        SELECT s.step_code,s.status,s.guidance,s.blocker,s.sort_order
                        FROM merchant_activation_steps s
                        JOIN merchant_activation_lifecycles l ON l.id=s.lifecycle_id
                        WHERE l.merchant_id=:merchantId ORDER BY s.sort_order
                        """,
                        params);
        Map<String, Object> current =
                steps.stream()
                        .filter(
                                step ->
                                        !Set.of("COMPLETED", "WAIVED", "WAIVED_LEGACY", "SKIPPED")
                                                .contains(String.valueOf(step.get("status"))))
                        .findFirst()
                        .orElse(steps.get(steps.size() - 1));
        boolean live =
                "PRODUCTION_ACTIVATED".equals(current.get("step_code"))
                        && "COMPLETED".equals(current.get("status"));
        String stepStatus = String.valueOf(current.get("status"));
        String lifecycleStatus =
                live
                        ? "LIVE"
                        : Set.of("BLOCKED", "FAILED", "NEEDS_RESUBMISSION").contains(stepStatus)
                                ? stepStatus
                                : String.valueOf(current.get("step_code"));
        jdbc.update(
                """
                UPDATE merchant_activation_lifecycles
                SET status=:status,current_step_code=:stepCode,next_action=:nextAction,
                    blocked_reason=:blocker,activated_at=CASE WHEN :status='LIVE' THEN COALESCE(activated_at,CURRENT_TIMESTAMP) ELSE activated_at END
                WHERE merchant_id=:merchantId
                """,
                params.addValue("status", lifecycleStatus)
                        .addValue("stepCode", current.get("step_code"))
                        .addValue(
                                "nextAction",
                                live
                                        ? "Monitor live processing and complete operational reviews."
                                        : current.get("guidance"))
                        .addValue("blocker", current.get("blocker")));
    }

    private record Step(String code, String name, String responsibleParty, String guidance) {}
}
