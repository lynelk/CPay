package net.citotech.cito.finance;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.admin.AdminApprovalService;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.admin.AdminPermissionService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P1 §1: enforceable settlement batch lifecycle.
 *
 * <p>Closes the P1 gap where {@code finance_settlement_batches} carried lifecycle columns but any
 * actor could flip {@code status} arbitrarily. Every maker-side step now requires the {@code
 * SETTLEMENT_MANAGE} permission, every state change is validated against the documented state
 * machine, approval flows through the P0 maker-checker {@link AdminApprovalService} (which enforces
 * maker != checker), blocking variance and unresolved high-severity exceptions block close, and
 * every applied transition writes an append-only {@code settlement_state_transitions} row plus an
 * enriched admin audit entry.
 */
@Service
public class SettlementLifecycleService {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CALCULATED = "CALCULATED";
    public static final String STATUS_REVIEW_PENDING = "REVIEW_PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_RECONCILED = "RECONCILED";
    public static final String STATUS_EXCEPTION = "EXCEPTION";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String PERMISSION_MANAGE = "SETTLEMENT_MANAGE";
    public static final String APPROVAL_TYPE = "SETTLEMENT_APPROVAL";

    private static final Set<String> VALID_STATES =
            Set.of(
                    STATUS_OPEN,
                    STATUS_CALCULATED,
                    STATUS_REVIEW_PENDING,
                    STATUS_APPROVED,
                    STATUS_PAID,
                    STATUS_RECONCILED,
                    STATUS_EXCEPTION,
                    STATUS_CLOSED);

    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS =
            Map.of(
                    STATUS_OPEN,
                    Set.of(STATUS_CALCULATED, STATUS_EXCEPTION),
                    STATUS_CALCULATED,
                    Set.of(STATUS_REVIEW_PENDING, STATUS_EXCEPTION, STATUS_OPEN),
                    STATUS_REVIEW_PENDING,
                    Set.of(STATUS_APPROVED, STATUS_EXCEPTION, STATUS_CALCULATED),
                    STATUS_APPROVED,
                    Set.of(STATUS_PAID, STATUS_EXCEPTION),
                    STATUS_PAID,
                    Set.of(STATUS_RECONCILED),
                    STATUS_RECONCILED,
                    Set.of(STATUS_CLOSED),
                    STATUS_EXCEPTION,
                    Set.of(STATUS_CALCULATED));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminApprovalService approvalService;
    private final AdminPermissionService permissions;
    private final AdminAuditService auditService;

    public SettlementLifecycleService(
            NamedParameterJdbcTemplate jdbcTemplate,
            AdminApprovalService approvalService,
            AdminPermissionService permissions,
            AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.approvalService = approvalService;
        this.permissions = permissions;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> calculate(long settlementId, String actor, String requestId) {
        permissions.require(
                PERMISSION_MANAGE, "settlement-calculate", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        transition(settlement, STATUS_CALCULATED, actor, "settlement-calculate", null, requestId);
        return result("settlement_calculated", settlementId);
    }

    /**
     * Maker step: submits a calculated settlement for checker approval. Records a P0 {@code
     * SETTLEMENT_APPROVAL} approval request so the checker step cannot be performed by the same
     * actor, and refuses to enqueue a second pending request for the same settlement.
     */
    @Transactional
    public Map<String, Object> submitForReview(long settlementId, String actor, String requestId) {
        permissions.require(
                PERMISSION_MANAGE, "settlement-submit-review", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        String reference = stringValue(settlement, "settlement_reference");
        approvalService.requireNoPendingFor(
                "SETTLEMENT", String.valueOf(settlementId), "settlement submit-review");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("settlementId", settlementId);
        payload.put("settlementReference", reference);
        payload.put("businessDate", stringValue(settlement, "business_date"));
        payload.put("currencyCode", stringValue(settlement, "currency_code"));
        payload.put("netAmount", settlement.get("net_amount"));
        long approvalRequestId =
                approvalService.create(
                        APPROVAL_TYPE,
                        "SETTLEMENT",
                        String.valueOf(settlementId),
                        payload,
                        stateHash(settlement.get("status")),
                        stateHash(STATUS_APPROVED),
                        requestId,
                        null);
        transition(
                settlement,
                STATUS_REVIEW_PENDING,
                actor,
                "settlement-submit-review",
                "approval_request_id=" + approvalRequestId,
                requestId);
        Map<String, Object> result = result("settlement_submitted_for_review", settlementId);
        result.put("approvalRequestId", approvalRequestId);
        return result;
    }

    /**
     * Checker step: approves the pending {@code SETTLEMENT_APPROVAL} request (enforcing a checker
     * different from the maker via {@link AdminApprovalService#approve}) and moves the settlement
     * to APPROVED.
     */
    @Transactional
    public Map<String, Object> approve(
            long settlementId, String checker, String note, String requestId) {
        Map<String, Object> settlement = requireSettlement(settlementId);
        long approvalRequestId =
                pendingApprovalRequestId(settlementId, settlementReference(settlement));
        approvalService.approve(approvalRequestId, checker, note);
        transition(
                settlement,
                STATUS_APPROVED,
                effectiveActor(checker),
                "settlement-approve",
                note,
                requestId);
        Map<String, Object> result = result("settlement_approved", settlementId);
        result.put("approvalRequestId", approvalRequestId);
        return result;
    }

    /**
     * Checker step: rejects the pending approval request and returns the settlement to CALCULATED.
     */
    @Transactional
    public Map<String, Object> rejectReview(
            long settlementId, String checker, String reason, String requestId) {
        Map<String, Object> settlement = requireSettlement(settlementId);
        long approvalRequestId =
                pendingApprovalRequestId(settlementId, settlementReference(settlement));
        approvalService.reject(approvalRequestId, checker, reason);
        transition(
                settlement,
                STATUS_CALCULATED,
                effectiveActor(checker),
                "settlement-review-rejected",
                reason,
                requestId);
        return result("settlement_review_rejected", settlementId);
    }

    @Transactional
    public Map<String, Object> markPaid(long settlementId, String actor, String requestId) {
        permissions.require(
                PERMISSION_MANAGE, "settlement-mark-paid", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        transition(settlement, STATUS_PAID, actor, "settlement-mark-paid", null, requestId);
        return result("settlement_marked_paid", settlementId);
    }

    @Transactional
    public Map<String, Object> reconcile(long settlementId, String actor, String requestId) {
        permissions.require(
                PERMISSION_MANAGE, "settlement-reconcile", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        transition(settlement, STATUS_RECONCILED, actor, "settlement-reconcile", null, requestId);
        return result("settlement_reconciled", settlementId);
    }

    /**
     * Closes a reconciled settlement. Refused while a blocking variance is recorded or any
     * HIGH/CRITICAL reconciliation exception for the batch is still open.
     */
    @Transactional
    public Map<String, Object> close(long settlementId, String actor, String requestId) {
        permissions.require(PERMISSION_MANAGE, "settlement-close", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        assertNoBlockingVariance(settlement);
        assertNoOpenBlockingExceptions(settlementId);
        transition(settlement, STATUS_CLOSED, actor, "settlement-close", null, requestId);
        return result("settlement_closed", settlementId);
    }

    @Transactional
    public Map<String, Object> flagException(
            long settlementId, String actor, String reason, String requestId) {
        permissions.require(
                PERMISSION_MANAGE, "settlement-flag-exception", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        transition(
                settlement,
                STATUS_EXCEPTION,
                actor,
                "settlement-flagged-exception",
                reason,
                requestId);
        return result("settlement_flagged_exception", settlementId);
    }

    @Transactional
    public Map<String, Object> reopenFromException(
            long settlementId, String actor, String reason, String requestId) {
        permissions.require(PERMISSION_MANAGE, "settlement-reopen", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        transition(
                settlement,
                STATUS_CALCULATED,
                actor,
                "settlement-reopened-from-exception",
                reason,
                requestId);
        return result("settlement_reopened", settlementId);
    }

    /**
     * Backward-compatible generic transition envelope used by the legacy {@code
     * /settlements/{id}/transition} endpoint. Refuses approval-guarded states (REVIEW_PENDING,
     * APPROVED) because those are only reachable through the maker-checker flow, keeping the
     * enforcement bypass-proof.
     */
    @Transactional
    public Map<String, Object> transitionStatus(
            long settlementId, String nextStatus, String actor, String reason, String requestId) {
        permissions.require(
                PERMISSION_MANAGE, "settlement-transition", "settlement:" + settlementId);
        Map<String, Object> settlement = requireSettlement(settlementId);
        if (STATUS_REVIEW_PENDING.equals(nextStatus) || STATUS_APPROVED.equals(nextStatus)) {
            throw new PaymentGatewayException(
                    "settlement "
                            + settlementId
                            + " cannot transition to "
                            + nextStatus
                            + " directly; use submit-review (maker) and approve (checker)");
        }
        transition(settlement, nextStatus, actor, "settlement-transition", reason, requestId);
        return result("settlement_transitioned", settlementId);
    }

    private void transition(
            Map<String, Object> settlement,
            String nextStatus,
            String actor,
            String actionName,
            String reason,
            String requestId) {
        String currentStatus = stringValue(settlement, "status");
        validateTarget(nextStatus);
        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of()).contains(nextStatus)) {
            throw new PaymentGatewayException(
                    "settlement "
                            + settlementId(settlement)
                            + " cannot transition from "
                            + currentStatus
                            + " to "
                            + nextStatus);
        }
        long id = settlementId(settlement);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("status", nextStatus);
        p.addValue("id", id);
        jdbcTemplate.update(
                """
                UPDATE finance_settlement_batches
                   SET status = :status,
                       calculated_at = CASE WHEN :status = 'CALCULATED' THEN CURRENT_TIMESTAMP ELSE calculated_at END,
                       review_requested_at = CASE WHEN :status = 'REVIEW_PENDING' THEN CURRENT_TIMESTAMP ELSE review_requested_at END,
                       approved_at = CASE WHEN :status = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE approved_at END,
                       paid_at = CASE WHEN :status = 'PAID' THEN CURRENT_TIMESTAMP ELSE paid_at END,
                       reconciled_at = CASE WHEN :status = 'RECONCILED' THEN CURRENT_TIMESTAMP ELSE reconciled_at END,
                       closed_at = CASE WHEN :status = 'CLOSED' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = :id
                """,
                p);
        insertTransitionAudit(
                id,
                settlementReference(settlement),
                currentStatus,
                nextStatus,
                effectiveActor(actor),
                reason,
                requestId);
        auditService.record(
                PERMISSION_MANAGE,
                actionName,
                "settlement:" + id,
                "settlement_reference="
                        + settlementReference(settlement)
                        + "; previous_status="
                        + currentStatus
                        + "; next_status="
                        + nextStatus
                        + (reason == null ? "" : "; reason=" + reason),
                new AdminAuditService.AuditContext(
                        null,
                        "SETTLEMENT",
                        String.valueOf(id),
                        stateHash(currentStatus),
                        stateHash(nextStatus),
                        reason,
                        requestId));
    }

    private void insertTransitionAudit(
            long settlementId,
            String reference,
            String previousStatus,
            String nextStatus,
            String actor,
            String reason,
            String requestId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("settlement_batch_id", settlementId);
        p.addValue("settlement_reference", reference);
        p.addValue("previous_status", previousStatus);
        p.addValue("next_status", nextStatus);
        p.addValue("actor", actor);
        p.addValue("reason", reason);
        p.addValue("request_id", requestId);
        jdbcTemplate.update(
                "INSERT INTO settlement_state_transitions "
                        + "(settlement_batch_id, settlement_reference, previous_status, next_status, "
                        + " actor, reason, request_id, transition_result) "
                        + "VALUES (:settlement_batch_id, :settlement_reference, :previous_status, "
                        + " :next_status, :actor, :reason, :request_id, 'APPLIED')",
                p);
    }

    private void assertNoBlockingVariance(Map<String, Object> settlement) {
        Object variance = settlement.get("variance_amount");
        if (variance != null && decimalValue(variance).signum() != 0) {
            throw new PaymentGatewayException(
                    "settlement "
                            + settlementId(settlement)
                            + " cannot close with blocking variance "
                            + variance);
        }
    }

    private void assertNoOpenBlockingExceptions(long settlementId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("settlement_batch_id", settlementId);
        Long openBlocking =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM reconciliation_exceptions "
                                + "WHERE settlement_batch_id = :settlement_batch_id "
                                + "AND severity IN ('HIGH', 'CRITICAL') "
                                + "AND status IN ('OPEN', 'ASSIGNED', 'UNDER_REVIEW')",
                        p,
                        Long.class);
        if (openBlocking != null && openBlocking > 0) {
            throw new PaymentGatewayException(
                    "settlement "
                            + settlementId
                            + " cannot close with "
                            + openBlocking
                            + " unresolved high-severity reconciliation exception(s)");
        }
    }

    private long pendingApprovalRequestId(long settlementId, String reference) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("resource_type", "SETTLEMENT");
        p.addValue("resource_id", String.valueOf(settlementId));
        p.addValue("approval_type", APPROVAL_TYPE);
        p.addValue("request_status", "PENDING_APPROVAL");
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id FROM approval_requests "
                                + "WHERE resource_type = :resource_type AND resource_id = :resource_id "
                                + "AND approval_type = :approval_type AND request_status = :request_status "
                                + "ORDER BY id DESC LIMIT 1",
                        p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "no pending SETTLEMENT_APPROVAL request for settlement "
                            + settlementId
                            + " ("
                            + reference
                            + ")");
        }
        return ((Number) rows.getFirst().get("id")).longValue();
    }

    private Map<String, Object> requireSettlement(long settlementId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT * FROM finance_settlement_batches WHERE id = :id",
                        new MapSqlParameterSource("id", settlementId));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("settlement " + settlementId + " not found");
        }
        return rows.getFirst();
    }

    private void validateTarget(String nextStatus) {
        if (!VALID_STATES.contains(nextStatus)) {
            throw new PaymentGatewayException("unknown settlement status " + nextStatus);
        }
    }

    private Map<String, Object> result(String action, long settlementId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("id", settlementId);
        result.put("status", "accepted");
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private long settlementId(Map<String, Object> settlement) {
        return ((Number) settlement.get("id")).longValue();
    }

    private String settlementReference(Map<String, Object> settlement) {
        return stringValue(settlement, "settlement_reference");
    }

    private String stringValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private String effectiveActor(String actor) {
        if (actor != null && !actor.isBlank()) {
            return actor.trim();
        }
        return "system";
    }

    private String stateHash(Object status) {
        String value = status == null ? "" : status.toString();
        return Integer.toHexString(value.hashCode());
    }

    private java.math.BigDecimal decimalValue(Object value) {
        if (value instanceof java.math.BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return java.math.BigDecimal.valueOf(number.doubleValue());
        }
        return new java.math.BigDecimal(String.valueOf(value));
    }
}
