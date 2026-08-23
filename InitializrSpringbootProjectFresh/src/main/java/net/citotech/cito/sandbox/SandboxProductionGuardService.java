package net.citotech.cito.sandbox;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.Model.Merchant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fail-closed production guard for go-live decisions and money-moving requests. */
@Service
public class SandboxProductionGuardService {
    private static final List<String> ROLLOUT_ORDER =
            List.of("SANDBOX", "COLLECTIONS", "REFUNDS", "PAYOUTS_LOW_LIMIT", "FULL");
    private static final Set<String> SMOKE_GATED_STAGES = Set.of("PAYOUTS_LOW_LIMIT", "FULL");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public SandboxProductionGuardService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Reserves one authoritative production usage slot before a money-moving command executes.
     * The unique merchant/operation/reference key makes a retry idempotent, while the row lock on
     * merchant_rollout_stages serializes concurrent quota checks for the same merchant.
     */
    @Transactional
    public void reserveProductionExecution(
            Merchant merchant, String environment, String operation, String requestReference) {
        if (!"PRODUCTION".equalsIgnoreCase(environment)) {
            return;
        }
        requireMerchant(merchant);
        String reference = requestReference == null ? "" : requestReference.trim();
        if (reference.isEmpty()) {
            throw new IllegalArgumentException("A stable request reference is required for production execution.");
        }
        String op = normalizeOperation(operation);
        String capabilityColumn = capabilityColumn(op);
        MapSqlParameterSource p = new MapSqlParameterSource("merchantId", merchant.getId());
        List<Map<String, Object>> stages =
                jdbcTemplate.queryForList(
                        "SELECT stage_code,production_daily_limit,"
                                + capabilityColumn
                                + " AS capability_enabled FROM merchant_rollout_stages "
                                + "WHERE merchant_id=:merchantId FOR UPDATE",
                        p);
        if (stages.isEmpty()) {
            throw new IllegalStateException(
                    "Merchant has not completed production rollout activation.");
        }
        Map<String, Object> rollout = stages.get(0);
        if (!truthy(rollout.get("capability_enabled"))) {
            throw new IllegalStateException(
                    "Production " + op + " is not enabled for this merchant's rollout stage.");
        }

        MapSqlParameterSource command = new MapSqlParameterSource();
        command.addValue("merchantId", merchant.getId());
        command.addValue("operation", op);
        command.addValue("reference", reference);
        Integer alreadyReserved =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchant_production_usage "
                                + "WHERE merchant_id=:merchantId AND operation=:operation "
                                + "AND request_reference=:reference",
                        command,
                        Integer.class);
        if (alreadyReserved != null && alreadyReserved > 0) {
            return;
        }

        int limit = number(rollout.get("production_daily_limit"));
        if (limit > 0) {
            Integer used =
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM merchant_production_usage "
                                    + "WHERE merchant_id=:merchantId AND usage_date=CURRENT_DATE()",
                            p,
                            Integer.class);
            int usedCount = used == null ? 0 : used;
            if (usedCount >= limit) {
                throw new IllegalStateException(
                        "Merchant production rollout daily limit reached ("
                                + usedCount
                                + "/"
                                + limit
                                + ").");
            }
        }

        jdbcTemplate.update(
                "INSERT INTO merchant_production_usage "
                        + "(merchant_id,operation,request_reference,usage_date) "
                        + "VALUES (:merchantId,:operation,:reference,CURRENT_DATE())",
                command);
    }

    /** Capability-only compatibility check. New money-moving paths should reserve usage instead. */
    public void enforcePayment(Merchant merchant, String environment, String operation) {
        if (!"PRODUCTION".equalsIgnoreCase(environment)) {
            return;
        }
        requireMerchant(merchant);
        String op = normalizeOperation(operation);
        String capabilityColumn = capabilityColumn(op);
        Integer enabled =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchant_rollout_stages WHERE merchant_id=:merchantId AND "
                                + capabilityColumn
                                + "=1",
                        new MapSqlParameterSource("merchantId", merchant.getId()),
                        Integer.class);
        if (enabled == null || enabled == 0) {
            throw new IllegalStateException(
                    "Production " + op + " is not enabled for this merchant's rollout stage.");
        }
    }

    public void assertDecisionAllowed(long requestId, String action, String actor) {
        MapSqlParameterSource p = new MapSqlParameterSource("requestId", requestId);
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT merchant_id,request_status,current_stage,decision_by "
                                + "FROM merchant_go_live_requests WHERE id=:requestId",
                        p);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Production-access request was not found.");
        }
        Map<String, Object> current = rows.get(0);
        String status = String.valueOf(current.get("request_status"));
        String stage = String.valueOf(current.get("current_stage"));
        if ("ACTIVATED".equals(status) || "REJECTED".equals(status)) {
            throw new IllegalStateException("This production-access request is already terminal.");
        }

        String normalizedAction = normalize(action, "ADVANCE");
        if ("REJECT".equals(normalizedAction)) {
            return;
        }
        String safeActor = actor == null ? "" : actor.trim();
        if (safeActor.isEmpty()) {
            throw new IllegalStateException("An authenticated administrator identity is required.");
        }
        String previousActor =
                current.get("decision_by") == null ? "" : String.valueOf(current.get("decision_by"));
        if (!previousActor.isBlank() && previousActor.equalsIgnoreCase(safeActor)) {
            throw new IllegalStateException(
                    "A different administrator must approve the next go-live review stage.");
        }
        if ("APPROVED".equals(stage) || "APPROVED".equals(status)) {
            long merchantId = ((Number) current.get("merchant_id")).longValue();
            MapSqlParameterSource promotion = new MapSqlParameterSource();
            promotion.addValue("requestId", requestId);
            promotion.addValue("merchantId", merchantId);
            Integer promoted =
                    jdbcTemplate.queryForObject(
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
        String stage = normalize(requestedStage, "SANDBOX");
        if (!ROLLOUT_ORDER.contains(stage)) {
            throw new IllegalArgumentException("Unsupported production rollout stage: " + stage);
        }
        if ("SANDBOX".equals(stage)) {
            return;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("merchantId", merchantId);
        Integer activated =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchant_go_live_requests WHERE merchant_id=:merchantId "
                                + "AND request_status='ACTIVATED'",
                        p,
                        Integer.class);
        if (activated == null || activated == 0) {
            throw new IllegalStateException(
                    "Merchant must complete the go-live approval workflow before production rollout.");
        }

        List<String> currentRows =
                jdbcTemplate.queryForList(
                        "SELECT stage_code FROM merchant_rollout_stages WHERE merchant_id=:merchantId",
                        p,
                        String.class);
        String current = currentRows.isEmpty() ? "SANDBOX" : normalize(currentRows.get(0), "SANDBOX");
        int currentIndex = ROLLOUT_ORDER.indexOf(current);
        int requestedIndex = ROLLOUT_ORDER.indexOf(stage);
        if (requestedIndex != currentIndex + 1) {
            throw new IllegalStateException(
                    "Production rollout must advance one stage at a time. Current stage is "
                            + current
                            + "; next allowed stage is "
                            + (currentIndex + 1 < ROLLOUT_ORDER.size()
                                    ? ROLLOUT_ORDER.get(currentIndex + 1)
                                    : "NONE")
                            + ".");
        }
        if (SMOKE_GATED_STAGES.contains(stage)) {
            Integer smokePassed =
                    jdbcTemplate.queryForObject(
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

    private void requireMerchant(Merchant merchant) {
        if (merchant == null || merchant.getId() == null) {
            throw new IllegalStateException("Verified merchant is required for production traffic.");
        }
    }

    private String capabilityColumn(String operation) {
        return switch (operation) {
            case "COLLECT", "COLLECTION", "PAYIN" -> "collections_enabled";
            case "REFUND", "REVERSAL" -> "refunds_enabled";
            case "PAYOUT", "DISBURSE", "BATCH_PAYOUT" -> "payouts_enabled";
            default ->
                    throw new IllegalArgumentException(
                            "Unsupported production operation: " + operation);
        };
    }

    private String normalizeOperation(String value) {
        return normalize(value, "UNKNOWN");
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim().toUpperCase(Locale.ROOT);
    }

    private int number(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "TRUE".equalsIgnoreCase(text) || "YES".equalsIgnoreCase(text);
    }
}
