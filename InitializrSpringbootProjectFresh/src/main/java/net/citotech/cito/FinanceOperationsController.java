package net.citotech.cito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * P1 finance and operations foundation endpoints.
 *
 * <p>These endpoints intentionally expose workflow envelopes and persistence boundaries for
 * settlement, treasury, reconciliation, daily close, exports and incident management. Domain
 * orchestration and maker-checker policy enforcement can be layered behind this contract without
 * changing the URL shape.
 */
@RestController
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping(
        path = "/api/v2/admin/finance-operations",
        produces = MediaType.APPLICATION_JSON_VALUE)
public class FinanceOperationsController {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public FinanceOperationsController(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/settlements")
    public Map<String, Object> listSettlements(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM finance_settlement_batches WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("settlements", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/settlements")
    public Map<String, Object> createSettlement(@RequestBody Map<String, Object> body) {
        String reference = reference("SETTLE");
        jdbcTemplate.update(
                """
                INSERT INTO finance_settlement_batches (
                    settlement_reference, merchant_id, provider_code, channel_code, country_code, currency_code,
                    business_date, settlement_cycle, status, gross_amount, fee_amount, tax_amount,
                    adjustment_amount, net_amount, variance_amount, item_count, created_by, finance_owner, notes, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'DAILY'), COALESCE(?, 'OPEN'),
                          COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0),
                          COALESCE(?, 0), COALESCE(?, 0), ?, ?, ?, ?)
                """,
                reference,
                optionalLong(body, "merchantId"),
                optionalString(body, "providerCode"),
                optionalString(body, "channelCode"),
                optionalString(body, "countryCode"),
                requiredString(body, "currencyCode"),
                requiredString(body, "businessDate"),
                optionalString(body, "settlementCycle"),
                optionalString(body, "status"),
                body.get("grossAmount"),
                body.get("feeAmount"),
                body.get("taxAmount"),
                body.get("adjustmentAmount"),
                body.get("netAmount"),
                body.get("varianceAmount"),
                body.get("itemCount"),
                optionalString(body, "createdBy"),
                optionalString(body, "financeOwner"),
                optionalString(body, "notes"),
                json(body));
        return created(reference, lastInsertId());
    }

    @GetMapping("/settlements/{id}")
    public Map<String, Object> getSettlement(@PathVariable Long id) {
        List<Map<String, Object>> settlement =
                jdbcTemplate.queryForList(
                        "SELECT * FROM finance_settlement_batches WHERE id = ?", id);
        if (settlement.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Settlement batch not found");
        }
        List<Map<String, Object>> items =
                jdbcTemplate.queryForList(
                        "SELECT * FROM finance_settlement_items WHERE settlement_batch_id = ? ORDER BY id",
                        id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settlement", settlement.getFirst());
        result.put("items", items);
        return result;
    }

    @Transactional
    @PostMapping("/settlements/{id}/items")
    public Map<String, Object> addSettlementItem(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO finance_settlement_items (
                    settlement_batch_id, transaction_reference, provider_reference, merchant_reference, transaction_type,
                    status, amount, fee_amount, tax_amount, net_amount, variance_amount, metadata
                ) VALUES (?, ?, ?, ?, ?, COALESCE(?, 'INCLUDED'), COALESCE(?, 0), COALESCE(?, 0),
                          COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), ?)
                """,
                id,
                requiredString(body, "transactionReference"),
                optionalString(body, "providerReference"),
                optionalString(body, "merchantReference"),
                requiredString(body, "transactionType"),
                optionalString(body, "status"),
                body.get("amount"),
                body.get("feeAmount"),
                body.get("taxAmount"),
                body.get("netAmount"),
                body.get("varianceAmount"),
                json(body));
        return accepted("settlement_item_recorded", id);
    }

    @Transactional
    @PostMapping("/settlements/{id}/transition")
    public Map<String, Object> transitionSettlement(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = requiredString(body, "status");
        String actor = optionalString(body, "actor");
        jdbcTemplate.update(
                """
                UPDATE finance_settlement_batches
                   SET status = ?,
                       calculated_at = CASE WHEN ? = 'CALCULATED' THEN CURRENT_TIMESTAMP ELSE calculated_at END,
                       review_requested_at = CASE WHEN ? = 'REVIEW_PENDING' THEN CURRENT_TIMESTAMP ELSE review_requested_at END,
                       approved_at = CASE WHEN ? = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE approved_at END,
                       paid_at = CASE WHEN ? = 'PAID' THEN CURRENT_TIMESTAMP ELSE paid_at END,
                       reconciled_at = CASE WHEN ? = 'RECONCILED' THEN CURRENT_TIMESTAMP ELSE reconciled_at END,
                       closed_at = CASE WHEN ? = 'CLOSED' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                       approved_by = CASE WHEN ? = 'APPROVED' THEN ? ELSE approved_by END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                status,
                status,
                status,
                status,
                status,
                status,
                status,
                status,
                actor,
                id);
        return accepted("settlement_transition_recorded", id);
    }

    @GetMapping("/treasury/positions")
    public Map<String, Object> listTreasuryPositions(
            @RequestParam(name = "merchantId", required = false) Long merchantId,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM treasury_positions WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (merchantId != null) {
            sql.append(" AND merchant_id = ?");
            params.add(merchantId);
        }
        sql.append(" ORDER BY position_date DESC, created_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("treasuryPositions", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/treasury/positions")
    public Map<String, Object> recordTreasuryPosition(@RequestBody Map<String, Object> body) {
        String reference = reference("TREASURY");
        jdbcTemplate.update(
                """
                INSERT INTO treasury_positions (
                    position_reference, merchant_id, provider_code, channel_code, country_code, currency_code, position_date,
                    available_balance, reserved_balance, pending_payout_exposure, unsettled_receivable, unsettled_payable,
                    unreconciled_exposure, source, captured_by, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0),
                          COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 0), COALESCE(?, 'INTERNAL'), ?, ?)
                """,
                reference,
                optionalLong(body, "merchantId"),
                optionalString(body, "providerCode"),
                optionalString(body, "channelCode"),
                optionalString(body, "countryCode"),
                requiredString(body, "currencyCode"),
                requiredString(body, "positionDate"),
                body.get("availableBalance"),
                body.get("reservedBalance"),
                body.get("pendingPayoutExposure"),
                body.get("unsettledReceivable"),
                body.get("unsettledPayable"),
                body.get("unreconciledExposure"),
                optionalString(body, "source"),
                optionalString(body, "capturedBy"),
                json(body));
        return created(reference, lastInsertId());
    }

    @GetMapping("/reconciliation/exceptions")
    public Map<String, Object> listReconciliationExceptions(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM reconciliation_exceptions WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok(
                "reconciliationExceptions",
                jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/reconciliation/exceptions")
    public Map<String, Object> createReconciliationException(
            @RequestBody Map<String, Object> body) {
        String reference = reference("RECON");
        jdbcTemplate.update(
                """
                INSERT INTO reconciliation_exceptions (
                    exception_reference, settlement_batch_id, transaction_reference, provider_reference, merchant_id,
                    provider_code, channel_code, currency_code, exception_type, severity, status, internal_amount,
                    provider_amount, variance_amount, assigned_to, metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, COALESCE(?, 'MEDIUM'), COALESCE(?, 'OPEN'), ?, ?, ?, ?, ?)
                """,
                reference,
                optionalLong(body, "settlementBatchId"),
                optionalString(body, "transactionReference"),
                optionalString(body, "providerReference"),
                optionalLong(body, "merchantId"),
                optionalString(body, "providerCode"),
                optionalString(body, "channelCode"),
                optionalString(body, "currencyCode"),
                requiredString(body, "exceptionType"),
                optionalString(body, "severity"),
                optionalString(body, "status"),
                body.get("internalAmount"),
                body.get("providerAmount"),
                body.get("varianceAmount"),
                optionalString(body, "assignedTo"),
                json(body));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/reconciliation/exceptions/{id}/resolve")
    public Map<String, Object> resolveReconciliationException(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                UPDATE reconciliation_exceptions
                   SET status = COALESCE(?, 'RESOLVED'), resolution_reason = ?, resolved_by = ?, resolved_at = CURRENT_TIMESTAMP,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                optionalString(body, "status"),
                requiredString(body, "resolutionReason"),
                optionalString(body, "resolvedBy"),
                id);
        return accepted("reconciliation_exception_resolved", id);
    }

    @GetMapping("/daily-close/{businessDate}")
    public Map<String, Object> getDailyClose(@PathVariable String businessDate) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT * FROM finance_daily_close_records WHERE business_date = ?",
                        businessDate);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily close record not found");
        }
        return ok("dailyClose", rows.getFirst());
    }

    @Transactional
    @PostMapping("/daily-close")
    public Map<String, Object> openDailyClose(@RequestBody Map<String, Object> body) {
        String reference = reference("CLOSE");
        jdbcTemplate.update(
                """
                INSERT INTO finance_daily_close_records (close_reference, business_date, opened_by, metadata)
                VALUES (?, ?, ?, ?)
                """,
                reference,
                requiredString(body, "businessDate"),
                optionalString(body, "openedBy"),
                json(body));
        return created(reference, lastInsertId());
    }

    @Transactional
    @PostMapping("/daily-close/{id}/decision")
    public Map<String, Object> decideDailyClose(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String status = requiredString(body, "status");

        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT * FROM finance_daily_close_records WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Daily close record not found");
        }
        Map<String, Object> current = rows.getFirst();

        String effectiveStatus = status;
        String effectiveReason = optionalString(body, "blockedReason");
        if ("APPROVED".equals(status) || "CLOSED".equals(status)) {
            List<String> blockers = new java.util.ArrayList<>();
            if (!effectiveBoolean(body.get("providerStatementsReceived"), current, "provider_statements_received")) {
                blockers.add("provider statements not received");
            }
            if (!effectiveBoolean(body.get("reconciliationImportCompleted"), current, "reconciliation_import_completed")) {
                blockers.add("reconciliation import not completed");
            }
            if (!effectiveBoolean(body.get("unmatchedItemsReviewed"), current, "unmatched_items_reviewed")) {
                blockers.add("unmatched items not reviewed");
            }
            if (!effectiveBoolean(body.get("highSeverityControlsResolved"), current, "high_severity_controls_resolved")) {
                blockers.add("high-severity controls unresolved");
            }
            if (!effectiveBoolean(body.get("makerCheckerApprovalsComplete"), current, "maker_checker_approvals_complete")) {
                blockers.add("maker-checker approvals incomplete");
            }
            if (!effectiveBoolean(body.get("financeOwnerSignedOff"), current, "finance_owner_signed_off")) {
                blockers.add("finance owner has not signed off");
            }

            Integer openCriticalExceptions =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*) FROM reconciliation_exceptions ex
                              JOIN finance_settlement_batches sb ON sb.id = ex.settlement_batch_id
                             WHERE sb.business_date = ?
                               AND ex.severity IN ('HIGH', 'CRITICAL')
                               AND ex.status IN ('OPEN', 'ASSIGNED', 'UNDER_REVIEW')
                            """,
                            Integer.class,
                            current.get("business_date"));
            if (openCriticalExceptions != null && openCriticalExceptions > 0) {
                blockers.add(openCriticalExceptions + " unresolved high-severity reconciliation exception(s)");
            }

            Integer unbalancedRuns =
                    jdbcTemplate.queryForObject(
                            """
                            SELECT COUNT(*) FROM ledger_trial_balance_runs
                             WHERE run_date = ? AND balanced_flag = 'NO'
                            """,
                            Integer.class,
                            current.get("business_date"));
            if (unbalancedRuns != null && unbalancedRuns > 0) {
                blockers.add(unbalancedRuns + " unbalanced trial-balance run(s) for the business date");
            }

            if (!blockers.isEmpty()) {
                effectiveStatus = "BLOCKED";
                effectiveReason =
                        effectiveReason == null || effectiveReason.isBlank()
                                ? String.join("; ", blockers)
                                : effectiveReason + "; " + String.join("; ", blockers);
            }
        }

        jdbcTemplate.update(
                """
                UPDATE finance_daily_close_records
                   SET status = ?, provider_statements_received = COALESCE(?, provider_statements_received),
                       reconciliation_import_completed = COALESCE(?, reconciliation_import_completed),
                       unmatched_items_reviewed = COALESCE(?, unmatched_items_reviewed),
                       high_severity_controls_resolved = COALESCE(?, high_severity_controls_resolved),
                       maker_checker_approvals_complete = COALESCE(?, maker_checker_approvals_complete),
                       finance_owner_signed_off = COALESCE(?, finance_owner_signed_off),
                       blocked_reason = ?, approved_by = CASE WHEN ? IN ('APPROVED', 'CLOSED') THEN ? ELSE approved_by END,
                       approved_at = CASE WHEN ? = 'APPROVED' THEN CURRENT_TIMESTAMP ELSE approved_at END,
                       closed_at = CASE WHEN ? = 'CLOSED' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                       updated_at = CURRENT_TIMESTAMP
                 WHERE id = ?
                """,
                effectiveStatus,
                body.get("providerStatementsReceived"),
                body.get("reconciliationImportCompleted"),
                body.get("unmatchedItemsReviewed"),
                body.get("highSeverityControlsResolved"),
                body.get("makerCheckerApprovalsComplete"),
                body.get("financeOwnerSignedOff"),
                effectiveReason,
                effectiveStatus,
                optionalString(body, "actor"),
                effectiveStatus,
                effectiveStatus,
                id);
        return accepted("daily_close_decision_recorded", id);
    }

    @Transactional
    @PostMapping("/reports/exports")
    public Map<String, Object> requestReportExport(@RequestBody Map<String, Object> body) {
        String reference = reference("EXPORT");
        jdbcTemplate.update(
                """
                INSERT INTO finance_report_exports (
                    export_reference, report_type, requested_by, format, date_from, date_to, merchant_id,
                    provider_code, channel_code, country_code, currency_code, filter_json
                ) VALUES (?, ?, ?, COALESCE(?, 'CSV'), ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reference,
                requiredString(body, "reportType"),
                optionalString(body, "requestedBy"),
                optionalString(body, "format"),
                optionalString(body, "dateFrom"),
                optionalString(body, "dateTo"),
                optionalLong(body, "merchantId"),
                optionalString(body, "providerCode"),
                optionalString(body, "channelCode"),
                optionalString(body, "countryCode"),
                optionalString(body, "currencyCode"),
                json(body));
        return created(reference, lastInsertId());
    }

    @GetMapping("/reports/exports")
    public Map<String, Object> listReportExports(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM finance_report_exports WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY requested_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("reportExports", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/incidents")
    public Map<String, Object> createIncident(@RequestBody Map<String, Object> body) {
        String reference = reference("INC");
        jdbcTemplate.update(
                """
                INSERT INTO operations_incidents (
                    incident_reference, title, severity, status, incident_type, provider_code, channel_code,
                    merchant_id, business_impact, owner, root_cause, corrective_action, metadata
                ) VALUES (?, ?, COALESCE(?, 'SEV3'), COALESCE(?, 'OPEN'), ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                reference,
                requiredString(body, "title"),
                optionalString(body, "severity"),
                optionalString(body, "status"),
                requiredString(body, "incidentType"),
                optionalString(body, "providerCode"),
                optionalString(body, "channelCode"),
                optionalLong(body, "merchantId"),
                optionalString(body, "businessImpact"),
                optionalString(body, "owner"),
                optionalString(body, "rootCause"),
                optionalString(body, "correctiveAction"),
                json(body));
        return created(reference, lastInsertId());
    }

    @GetMapping("/incidents")
    public Map<String, Object> listIncidents(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "limit", defaultValue = "50") int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM operations_incidents WHERE 1=1");
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY detected_at DESC LIMIT ?");
        params.add(safeLimit(limit));
        return ok("incidents", jdbcTemplate.queryForList(sql.toString(), params.toArray()));
    }

    @Transactional
    @PostMapping("/incidents/{id}/events")
    public Map<String, Object> addIncidentEvent(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        jdbcTemplate.update(
                """
                INSERT INTO operations_incident_events (incident_id, event_type, actor, message, evidence_url, metadata)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                requiredString(body, "eventType"),
                optionalString(body, "actor"),
                optionalString(body, "message"),
                optionalString(body, "evidenceUrl"),
                json(body));
        return accepted("incident_event_recorded", id);
    }

    private Long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private Map<String, Object> ok(String key, Object value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(key, value);
        result.put("generatedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> created(String reference, Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("reference", reference);
        result.put("status", "created");
        return result;
    }

    private Map<String, Object> accepted(String action, Object id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("action", action);
        result.put("id", id);
        result.put("status", "accepted");
        return result;
    }

    private String reference(String prefix) {
        return prefix
                + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    private int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 250));
    }

    private String requiredString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Missing required field: " + field);
        }
        return value.toString();
    }

    private String optionalString(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return value.toString();
    }

    private Long optionalLong(Map<String, Object> body, String field) {
        Object value = body.get(field);
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String json(Map<String, Object> body) {
        try {
            return objectMapper.writeValueAsString(body == null ? Map.of() : body);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload", e);
        }
    }

    private boolean effectiveBoolean(
            Object submitted, Map<String, Object> current, String column) {
        if (submitted instanceof Boolean bool) {
            return bool;
        }
        Object stored = current.get(column);
        if (stored instanceof Boolean bool) {
            return bool;
        }
        if (stored instanceof Number number) {
            return number.intValue() != 0;
        }
        if (stored != null) {
            return Boolean.parseBoolean(stored.toString());
        }
        return false;
    }
}
