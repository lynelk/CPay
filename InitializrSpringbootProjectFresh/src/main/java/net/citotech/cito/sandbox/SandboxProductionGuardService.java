package net.citotech.cito.sandbox;

import java.util.Locale;
import java.util.Set;
import net.citotech.cito.Model.Merchant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Fail-closed production guard for go-live decisions and money-moving requests.
 * Sandbox traffic is deliberately unaffected.
 */
@Service
public class SandboxProductionGuardService {
    private static final Set<String> SMOKE_GATED_STAGES = Set.of("PAYOUTS_LOW_LIMIT", "FULL");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SandboxProductionGuardService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void enforcePayment(Merchant merchant, String environment, String operation) {
        if (!"PRODUCTION".equalsIgnoreCase(environment)) {
            return;
        }
        if (merchant == null || merchant.getId() == null) {
            throw new IllegalStateException("Verified merchant is required for production traffic.");
        }
        String op = normalize(operation);
        MapSqlParameterSource p = new MapSqlParameterSource("merchantId", merchant.getId());
        String capabilityColumn = switch (op) {
            case "COLLECT", "COLLECTION", "PAYIN" -> "collections_enabled";
            case "REFUND", "REVERSAL" -> "refunds_enabled";
            case "PAYOUT", "DISBURSE", "BATCH_PAYOUT" -> "payouts_enabled";
            default -> throw new IllegalArgumentException("Unsupported production operation: " + operation);
        };
        Integer enabled = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_rollout_stages WHERE merchant_id=:merchantId AND "
                        + capabilityColumn + "=1",
                p,
                Integer.class);
        if (enabled == null || enabled == 0) {
            throw new IllegalStateException(
                    "Production " + op + " is not enabled for this merchant's rollout stage.");
        }

        Integer limit = jdbcTemplate.queryForObject(
                "SELECT production_daily_limit FROM merchant_rollout_stages WHERE merchant_id=:merchantId",
                p,
                Integer.class);
        if (limit == null || limit <= 0) {
            return;
        }
        MapSqlParameterSource usage = new MapSqlParameterSource();
        usage.addValue("merchantNumber", merchant.getAccount_number());
        Integer used = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM provider_endpoint_runs WHERE merchant_number=:merchantNumber "
                        + "AND environment='PRODUCTION' AND created_at>=CURRENT_DATE()",
                usage,
                Integer.class);
        int usedCount = used == null ? 0 : used;
        if (usedCount >= limit) {
            throw new IllegalStateException(
                    "Merchant production rollout daily limit reached ("
                            + usedCount + "/" + limit + ").");
        }
    }

    public void assertDecisionAllowed(long requestId, String action, String actor) {
        MapSqlParameterSource p = new MapSqlParameterSource("requestId", requestId);
        var rows = jdbcTemplate.queryForList(
                "SELECT merchant_id,request_status,current_stage,decision_by FROM merchant_go_live_requests WHERE id=:requestId",
                p);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Production-access request was not found.");
        }
        var current = rows.get(0);
        String status = String.valueOf(current.get("request_status"));
        String stage = String.valueOf(current.get("current_stage"));
        String previousActor = current.get("decision_by") == null ? "" : String.valueOf(current.get("decision_by"));
        String normalizedAction = normalize(action);
        if ("REJECT".equals(normalizedAction)) {
            return;
        }
        if ("ACTIVATED".equals(status) || "REJECTED".equals(status)) {
            throw new IllegalStateException("This production-access request is already terminal.");
        }
        if (!previousActor.isBlank() && previousActor.equalsIgnoreCase(actor)) {
            throw new IllegalStateException(
                    "A different administrator must approve the next go-live review stage.");
        }
        if ("APPROVED".equals(stage) || "APPROVED".equals(status)) {
            long merchantId = ((Number) current.get("merchant_id")).longValue();
            MapSqlParameterSource promotion = new MapSqlParameterSource();
            promotion.addValue("requestId", requestId);
            promotion.addValue("merchantId", merchantId);
            Integer promoted = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sandbox_configuration_promotions "
                            + "WHERE go_live_request_id=:requestId AND merchant_id=:merchantId "
                            + "AND promotion_status='PROMOTED'",
                    promotion,
                    Integer.class);
            if (promoted == null || promoted == 0) {
                throw new IllegalStateException(
                        "Approved sandbox configuration must be promoted before production activation.");
            }
        }
    }

    public void assertRolloutStageAllowed(long merchantId, String requestedStage) {
        String stage = normalize(requestedStage);
        if ("SANDBOX".equals(stage)) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("merchantId", merchantId);
        Integer activated = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_go_live_requests WHERE merchant_id=:merchantId "
                        + "AND request_status='ACTIVATED'",
                p,
                Integer.class);
        if (activated == null || activated == 0) {
            throw new IllegalStateException(
                    "Merchant must complete the go-live approval workflow before production rollout.");
        }
        if (SMOKE_GATED_STAGES.contains(stage)) {
            Integer smokePassed = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM sandbox_live_smoke_tests WHERE merchant_id=:merchantId "
                            + "AND test_status='PASSED'",
                    p,
                    Integer.class);
            if (smokePassed == null || smokePassed == 0) {
                throw new IllegalStateException(
                        "A passing controlled production smoke test is required before enabling payout/full rollout stages.");
            }
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
                ? "ADVANCE"
                : value.trim().toUpperCase(Locale.ROOT);
    }
}
