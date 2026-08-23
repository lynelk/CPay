package net.citotech.cito.sandbox;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes go-live state using the current merchant_feature_flags(flag_key) schema. */
@Service
public class SandboxGoLiveControlService {
    private static final List<String> GO_LIVE_STAGES =
            List.of(
                    "TECHNICAL_REVIEW",
                    "COMPLIANCE_REVIEW",
                    "RISK_REVIEW",
                    "OPS_REVIEW",
                    "APPROVED",
                    "ACTIVATED");
    private static final Set<String> ROLLOUT_STAGES =
            Set.of("SANDBOX", "COLLECTIONS", "REFUNDS", "PAYOUTS_LOW_LIMIT", "FULL");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SandboxLifecycleService lifecycleService;

    public SandboxGoLiveControlService(
            NamedParameterJdbcTemplate jdbcTemplate, SandboxLifecycleService lifecycleService) {
        this.jdbcTemplate = jdbcTemplate;
        this.lifecycleService = lifecycleService;
    }

    @Transactional
    public Map<String, Object> requestProductionAccess(long merchantId, String actor) {
        Map<String, Object> certification = lifecycleService.latestCertification(merchantId);
        if (!"PASSED".equals(String.valueOf(certification.get("run_status")))) {
            throw new IllegalStateException(
                    "A passing sandbox certification run is required before production access can be requested.");
        }
        MapSqlParameterSource merchant = new MapSqlParameterSource("merchantId", merchantId);
        Integer active = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_go_live_requests WHERE merchant_id=:merchantId "
                        + "AND request_status IN ('REQUESTED','IN_REVIEW','APPROVED')",
                merchant,
                Integer.class);
        if (active != null && active > 0) {
            throw new IllegalStateException(
                    "A production-access request is already active for this merchant.");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("runId", certification.get("id"));
        p.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO merchant_go_live_requests "
                        + "(merchant_id,certification_run_id,request_status,current_stage,requested_by) "
                        + "VALUES (:merchantId,:runId,'REQUESTED','TECHNICAL_REVIEW',:actor)",
                p);
        setRolloutStage(merchantId, "SANDBOX", actor, 0);
        return lifecycleService.latestGoLiveRequest(merchantId);
    }

    @Transactional
    public Map<String, Object> advanceGoLiveRequest(
            long requestId, String action, String actor, String notes) {
        MapSqlParameterSource p = new MapSqlParameterSource("requestId", requestId);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT merchant_id,request_status,current_stage FROM merchant_go_live_requests WHERE id=:requestId",
                p);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Production-access request was not found.");
        }
        Map<String, Object> current = rows.get(0);
        long merchantId = ((Number) current.get("merchant_id")).longValue();
        String status = String.valueOf(current.get("request_status"));
        String stage = String.valueOf(current.get("current_stage"));
        String normalizedAction = normalize(action, "ADVANCE");
        if ("REJECT".equals(normalizedAction)) {
            updateRequest(requestId, "REJECTED", stage, actor, notes, false, false);
            return lifecycleService.latestGoLiveRequest(merchantId);
        }
        if ("ACTIVATED".equals(status) || "REJECTED".equals(status)) {
            throw new IllegalStateException("This production-access request is already terminal.");
        }
        int index = GO_LIVE_STAGES.indexOf(stage);
        if (index < 0) {
            throw new IllegalStateException("Unknown go-live review stage: " + stage);
        }
        String next = index + 1 < GO_LIVE_STAGES.size() ? GO_LIVE_STAGES.get(index + 1) : stage;
        String nextStatus = "APPROVED".equals(next)
                ? "APPROVED"
                : "ACTIVATED".equals(next) ? "ACTIVATED" : "IN_REVIEW";
        updateRequest(
                requestId,
                nextStatus,
                next,
                actor,
                notes,
                "APPROVED".equals(next),
                "ACTIVATED".equals(next));
        if ("ACTIVATED".equals(next)) {
            setRolloutStage(merchantId, "COLLECTIONS", actor, 10);
        }
        return lifecycleService.latestGoLiveRequest(merchantId);
    }

    @Transactional
    public Map<String, Object> setRolloutStage(
            long merchantId, String requestedStage, String actor, Integer requestedLimit) {
        String stage = normalize(requestedStage, "SANDBOX");
        if (!ROLLOUT_STAGES.contains(stage)) {
            throw new IllegalArgumentException("Unsupported production rollout stage: " + stage);
        }
        int defaultLimit = switch (stage) {
            case "SANDBOX" -> 0;
            case "COLLECTIONS" -> 10;
            case "REFUNDS" -> 25;
            case "PAYOUTS_LOW_LIMIT" -> 50;
            case "FULL" -> 1000000;
            default -> 0;
        };
        int limit = requestedLimit == null ? defaultLimit : Math.max(0, requestedLimit);
        boolean collections = !"SANDBOX".equals(stage);
        boolean refunds = Set.of("REFUNDS", "PAYOUTS_LOW_LIMIT", "FULL").contains(stage);
        boolean payouts = Set.of("PAYOUTS_LOW_LIMIT", "FULL").contains(stage);

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("stage", stage);
        p.addValue("limit", limit);
        p.addValue("collections", collections ? 1 : 0);
        p.addValue("refunds", refunds ? 1 : 0);
        p.addValue("payouts", payouts ? 1 : 0);
        p.addValue("actor", actor);
        jdbcTemplate.update(
                "INSERT INTO merchant_rollout_stages "
                        + "(merchant_id,stage_code,production_daily_limit,collections_enabled,refunds_enabled,payouts_enabled,updated_by) "
                        + "VALUES (:merchantId,:stage,:limit,:collections,:refunds,:payouts,:actor) "
                        + "ON DUPLICATE KEY UPDATE stage_code=:stage,production_daily_limit=:limit," 
                        + "collections_enabled=:collections,refunds_enabled=:refunds,payouts_enabled=:payouts,updated_by=:actor",
                p);
        upsertFeature(merchantId, "production-collections", collections);
        upsertFeature(merchantId, "production-refunds", refunds);
        upsertFeature(merchantId, "production-payouts", payouts);
        return lifecycleService.rollout(merchantId);
    }

    private void upsertFeature(long merchantId, String flagKey, boolean enabled) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchantId", merchantId);
        p.addValue("flagKey", flagKey);
        p.addValue("enabled", enabled ? 1 : 0);
        p.addValue("description", "Managed by sandbox-to-production rollout");
        jdbcTemplate.update(
                "INSERT INTO merchant_feature_flags (merchant_id,flag_key,enabled,description) "
                        + "VALUES (:merchantId,:flagKey,:enabled,:description) "
                        + "ON DUPLICATE KEY UPDATE enabled=:enabled,description=:description",
                p);
    }

    private void updateRequest(
            long requestId,
            String status,
            String stage,
            String actor,
            String notes,
            boolean approved,
            boolean activated) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", requestId);
        p.addValue("status", status);
        p.addValue("stage", stage);
        p.addValue("actor", actor);
        p.addValue("notes", notes);
        String timestamps = approved
                ? ",approved_at=CURRENT_TIMESTAMP"
                : activated ? ",activated_at=CURRENT_TIMESTAMP" : "";
        jdbcTemplate.update(
                "UPDATE merchant_go_live_requests SET request_status=:status,current_stage=:stage," 
                        + "decision_by=:actor,decision_notes=:notes"
                        + timestamps
                        + " WHERE id=:id",
                p);
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
