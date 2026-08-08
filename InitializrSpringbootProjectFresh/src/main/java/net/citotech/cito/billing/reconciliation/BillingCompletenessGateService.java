package net.citotech.cito.billing.reconciliation;

import java.util.List;
import java.util.Map;
import net.citotech.cito.billing.invoicing.BillingInvoiceRecord;
import net.citotech.cito.billing.invoicing.BillingInvoiceRepository;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The maker-checker gate a DRAFT {@code billing_invoices} row (V47) must pass through to become
 * FINALIZED (Flyway {@code V49}), mirroring {@code
 * net.citotech.cito.reconciliation.FinanceWorkflowService}'s maker/checker shape: {@link #submit}
 * is the maker action, {@link #approve}/{@link #reject} are checker actions, and the checker must
 * differ from the maker (enforced by the {@code requested_by<>approved_by}/{@code
 * requested_by<>rejected_by} SQL guard, same as {@code FinanceWorkflowService}).
 *
 * <p>Unlike {@code FinanceWorkflowService}'s fail-closed variance tolerance, a FAILed completeness
 * result does not block submission - it is recorded, and a checker may still {@link #approve} it by
 * supplying a {@code waiverReason} (the "checker-waiver" path named in the Phase 3 exit criterion),
 * so a legitimately-excluded charge does not force endless re-submission.
 */
@Service
public class BillingCompletenessGateService {
    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_APPROVED = "APPROVED";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BillingInvoiceRepository invoiceRepository;

    public BillingCompletenessGateService(
            NamedParameterJdbcTemplate jdbcTemplate, BillingInvoiceRepository invoiceRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.invoiceRepository = invoiceRepository;
    }

    /**
     * Maker submit: computes completeness (any {@code CUSTOMER_CHARGE} rated charge for the
     * invoice's tenant/currency/period still unstaged means FAIL) and writes/updates a {@code
     * PENDING_APPROVAL} gate row. Re-submitting after a checker rejection is expected and safe; an
     * already-{@code APPROVED} gate is never reopened - throws instead. Returns the gate row id.
     */
    public long submit(long billingInvoiceId, String requestedBy) {
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new PaymentGatewayException("Completeness gate submission requires a requester");
        }
        BillingInvoiceRecord invoice =
                invoiceRepository
                        .find(billingInvoiceId)
                        .orElseThrow(
                                () ->
                                        new PaymentGatewayException(
                                                "Billing invoice not found: " + billingInvoiceId));
        if (!invoice.isDraft()) {
            throw new PaymentGatewayException(
                    "Billing invoice "
                            + billingInvoiceId
                            + " is not DRAFT (status="
                            + invoice.status()
                            + ")");
        }

        int unstagedCount =
                invoiceRepository.countUnstagedCustomerCharges(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        invoice.periodStart(),
                        invoice.periodEnd());
        String completenessResult = unstagedCount == 0 ? RESULT_PASS : RESULT_FAIL;

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("billing_tenant_id", invoice.billingTenantId());
        p.addValue("completeness_result", completenessResult);
        p.addValue("unstaged_charge_count", unstagedCount);
        p.addValue("requested_by", requestedBy.trim());
        // Upsert that keeps an already-APPROVED row APPROVED (never reopened) and otherwise
        // refreshes the completeness snapshot for a fresh PENDING_APPROVAL submission - same shape
        // as FinanceWorkflowService.submitDailyClose's CASE-guarded ON DUPLICATE KEY UPDATE.
        jdbcTemplate.update(
                "INSERT INTO billing_completeness_gates (billing_invoice_id, billing_tenant_id, "
                        + "gate_status, completeness_result, unstaged_charge_count, requested_by, requested_at) "
                        + "VALUES (:billing_invoice_id, :billing_tenant_id, 'PENDING_APPROVAL', "
                        + ":completeness_result, :unstaged_charge_count, :requested_by, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "gate_status = CASE WHEN gate_status='APPROVED' THEN gate_status ELSE 'PENDING_APPROVAL' END, "
                        + "completeness_result = CASE WHEN gate_status='APPROVED' THEN completeness_result ELSE VALUES(completeness_result) END, "
                        + "unstaged_charge_count = CASE WHEN gate_status='APPROVED' THEN unstaged_charge_count ELSE VALUES(unstaged_charge_count) END, "
                        + "requested_by = CASE WHEN gate_status='APPROVED' THEN requested_by ELSE VALUES(requested_by) END, "
                        + "requested_at = CASE WHEN gate_status='APPROVED' THEN requested_at ELSE CURRENT_TIMESTAMP END",
                p);

        String status =
                jdbcTemplate.queryForObject(
                        "SELECT gate_status FROM billing_completeness_gates WHERE billing_invoice_id=:billing_invoice_id",
                        p,
                        String.class);
        if (STATUS_APPROVED.equals(status)) {
            throw new PaymentGatewayException(
                    "Completeness gate already approved for invoice " + billingInvoiceId);
        }
        return jdbcTemplate.queryForObject(
                "SELECT id FROM billing_completeness_gates WHERE billing_invoice_id=:billing_invoice_id",
                p,
                Long.class);
    }

    /**
     * Checker approval: the approver must differ from the maker and the gate must still be {@code
     * PENDING_APPROVAL}. A {@code FAIL} result additionally requires a non-blank {@code
     * waiverReason} - approving a passing gate ignores it. Returns rows affected (0 = same-actor
     * approve, not pending, or missing gate).
     */
    public int approve(long billingInvoiceId, String approvedBy, String waiverReason) {
        if (approvedBy == null || approvedBy.isBlank()) {
            throw new PaymentGatewayException("Completeness gate approval requires an approver");
        }
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT completeness_result FROM billing_completeness_gates "
                                + "WHERE billing_invoice_id=:billing_invoice_id AND gate_status='PENDING_APPROVAL'",
                        new MapSqlParameterSource("billing_invoice_id", billingInvoiceId));
        if (rows.isEmpty()) {
            return 0;
        }
        String completenessResult = String.valueOf(rows.get(0).get("completeness_result"));
        boolean isWaiver = RESULT_FAIL.equals(completenessResult);
        if (isWaiver && (waiverReason == null || waiverReason.isBlank())) {
            throw new PaymentGatewayException(
                    "Approving a FAILED completeness gate for invoice "
                            + billingInvoiceId
                            + " requires a waiver reason");
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("approved_by", approvedBy.trim());
        p.addValue("waiver_reason", isWaiver ? waiverReason.trim() : null);
        return jdbcTemplate.update(
                "UPDATE billing_completeness_gates SET gate_status='APPROVED', approved_by=:approved_by, "
                        + "approved_at=CURRENT_TIMESTAMP, rejection_reason=NULL, waiver_reason=:waiver_reason "
                        + "WHERE billing_invoice_id=:billing_invoice_id AND gate_status='PENDING_APPROVAL' "
                        + "AND requested_by IS NOT NULL AND requested_by<>:approved_by",
                p);
    }

    /**
     * Checker rejection: records the reason and keeps the gate {@code PENDING_APPROVAL} so the
     * maker can correct and re-{@link #submit}. Returns rows affected (0 = same-actor reject or not
     * pending).
     */
    public int reject(long billingInvoiceId, String rejectedBy, String reason) {
        if (rejectedBy == null || rejectedBy.isBlank()) {
            throw new PaymentGatewayException("Completeness gate rejection requires a rejector");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("rejected_by", rejectedBy.trim());
        p.addValue(
                "reason",
                reason == null || reason.isBlank() ? "Rejected by checker" : reason.trim());
        return jdbcTemplate.update(
                "UPDATE billing_completeness_gates SET rejection_reason=:reason "
                        + "WHERE billing_invoice_id=:billing_invoice_id AND gate_status='PENDING_APPROVAL' "
                        + "AND requested_by IS NOT NULL AND requested_by<>:rejected_by",
                p);
    }

    /** Whether this invoice's completeness gate has reached {@code APPROVED}. */
    public boolean isApproved(long billingInvoiceId) {
        List<String> statuses =
                jdbcTemplate.query(
                        "SELECT gate_status FROM billing_completeness_gates WHERE billing_invoice_id=:billing_invoice_id",
                        new MapSqlParameterSource("billing_invoice_id", billingInvoiceId),
                        (rs, rowNum) -> rs.getString("gate_status"));
        return !statuses.isEmpty() && STATUS_APPROVED.equals(statuses.get(0));
    }
}
