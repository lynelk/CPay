package net.citotech.cito.billing.reconciliation;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import net.citotech.cito.billing.invoicing.BillingInvoiceRecord;
import net.citotech.cito.billing.invoicing.BillingInvoiceRepository;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Maker-checker completeness gate for periodic billing invoice finalization. */
@Service
public class BillingCompletenessGateService {
    private static final String RESULT_PASS = "PASS";
    private static final String RESULT_FAIL = "FAIL";
    private static final String STATUS_APPROVED = "APPROVED";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final BillingInvoiceRepository invoiceRepository;

    public BillingCompletenessGateService(
            NamedParameterJdbcTemplate jdbcTemplate, BillingInvoiceRepository invoiceRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.invoiceRepository = invoiceRepository;
    }

    public long submit(long billingInvoiceId, String requestedBy) {
        if (requestedBy == null || requestedBy.isBlank()) {
            throw new PaymentGatewayException("Completeness gate submission requires a requester");
        }
        BillingInvoiceRecord invoice = requireDraftInvoice(billingInvoiceId);
        int unstagedCount =
                invoiceRepository.countUnstagedCustomerCharges(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        invoice.periodStart(),
                        invoice.periodEnd());
        int watermarkFailures = countIncompleteWatermarks(invoice);
        int materialExceptions =
                countOpenExceptions(invoice.billingTenantId(), invoice.id(), false);
        String result =
                unstagedCount == 0 && watermarkFailures == 0 && materialExceptions == 0
                        ? RESULT_PASS
                        : RESULT_FAIL;

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("billing_tenant_id", invoice.billingTenantId());
        p.addValue("completeness_result", result);
        p.addValue("unstaged_charge_count", unstagedCount);
        p.addValue("source_watermark_failure_count", watermarkFailures);
        p.addValue("material_exception_count", materialExceptions);
        p.addValue("requested_by", requestedBy.trim());
        jdbcTemplate.update(
                "INSERT INTO billing_completeness_gates (billing_invoice_id,billing_tenant_id,gate_status,"
                        + "completeness_result,unstaged_charge_count,source_watermark_failure_count,material_exception_count,requested_by,requested_at) "
                        + "VALUES (:billing_invoice_id,:billing_tenant_id,'PENDING_APPROVAL',:completeness_result,"
                        + ":unstaged_charge_count,:source_watermark_failure_count,:material_exception_count,:requested_by,CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE "
                        + "gate_status=CASE WHEN gate_status='APPROVED' THEN gate_status ELSE 'PENDING_APPROVAL' END,"
                        + "completeness_result=CASE WHEN gate_status='APPROVED' THEN completeness_result ELSE VALUES(completeness_result) END,"
                        + "unstaged_charge_count=CASE WHEN gate_status='APPROVED' THEN unstaged_charge_count ELSE VALUES(unstaged_charge_count) END,"
                        + "source_watermark_failure_count=CASE WHEN gate_status='APPROVED' THEN source_watermark_failure_count ELSE VALUES(source_watermark_failure_count) END,"
                        + "material_exception_count=CASE WHEN gate_status='APPROVED' THEN material_exception_count ELSE VALUES(material_exception_count) END,"
                        + "requested_by=CASE WHEN gate_status='APPROVED' THEN requested_by ELSE VALUES(requested_by) END,"
                        + "requested_at=CASE WHEN gate_status='APPROVED' THEN requested_at ELSE CURRENT_TIMESTAMP END",
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
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM billing_completeness_gates WHERE billing_invoice_id=:billing_invoice_id",
                        p,
                        Long.class);
        return id == null ? 0L : id;
    }

    public int approve(long billingInvoiceId, String approvedBy, String waiverReason) {
        if (approvedBy == null || approvedBy.isBlank()) {
            throw new PaymentGatewayException("Completeness gate approval requires an approver");
        }
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT completeness_result,billing_tenant_id FROM billing_completeness_gates "
                                + "WHERE billing_invoice_id=:billing_invoice_id AND gate_status='PENDING_APPROVAL'",
                        new MapSqlParameterSource("billing_invoice_id", billingInvoiceId));
        if (rows.isEmpty()) {
            return 0;
        }
        Map<String, Object> gate = rows.get(0);
        String completenessResult = String.valueOf(gate.get("completeness_result"));
        boolean waiver = RESULT_FAIL.equals(completenessResult);
        if (waiver && (waiverReason == null || waiverReason.isBlank())) {
            throw new PaymentGatewayException(
                    "Approving a FAILED completeness gate for invoice "
                            + billingInvoiceId
                            + " requires a waiver reason");
        }
        if (waiver
                && countOpenExceptions(
                                number(gate.get("billing_tenant_id")), billingInvoiceId, true)
                        > 0) {
            throw new PaymentGatewayException(
                    "CRITICAL billing operational exceptions cannot be waived at invoice finalization");
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("approved_by", approvedBy.trim());
        p.addValue("waiver_reason", waiver ? waiverReason.trim() : null);
        return jdbcTemplate.update(
                "UPDATE billing_completeness_gates SET gate_status='APPROVED',approved_by=:approved_by,"
                        + "approved_at=CURRENT_TIMESTAMP,rejection_reason=NULL,waiver_reason=:waiver_reason "
                        + "WHERE billing_invoice_id=:billing_invoice_id AND gate_status='PENDING_APPROVAL' "
                        + "AND requested_by IS NOT NULL AND requested_by<>:approved_by",
                p);
    }

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

    /** Whether the maker-checker gate itself has reached APPROVED. */
    public boolean isApproved(long billingInvoiceId) {
        List<String> statuses =
                jdbcTemplate.query(
                        "SELECT gate_status FROM billing_completeness_gates WHERE billing_invoice_id=:id",
                        new MapSqlParameterSource("id", billingInvoiceId),
                        (rs, rowNum) -> rs.getString("gate_status"));
        return !statuses.isEmpty() && STATUS_APPROVED.equals(statuses.get(0));
    }

    /**
     * Revalidates operational completeness immediately before money is posted. A new source failure
     * or material exception after approval invalidates a clean PASS; a waived FAIL remains usable
     * only while its failure counts have not grown. CRITICAL exceptions always fail closed.
     */
    public boolean isFinalizationReady(long billingInvoiceId) {
        List<Map<String, Object>> gates =
                jdbcTemplate.queryForList(
                        "SELECT gate_status,waiver_reason,unstaged_charge_count,source_watermark_failure_count,material_exception_count "
                                + "FROM billing_completeness_gates WHERE billing_invoice_id=:id",
                        new MapSqlParameterSource("id", billingInvoiceId));
        if (gates.isEmpty()
                || !STATUS_APPROVED.equals(String.valueOf(gates.get(0).get("gate_status")))) {
            return false;
        }
        BillingInvoiceRecord invoice = invoiceRepository.find(billingInvoiceId).orElse(null);
        if (invoice == null
                || countOpenExceptions(invoice.billingTenantId(), invoice.id(), true) > 0) {
            return false;
        }
        Map<String, Object> gate = gates.get(0);
        int currentUnstaged =
                invoiceRepository.countUnstagedCustomerCharges(
                        invoice.billingTenantId(),
                        invoice.currency(),
                        invoice.periodStart(),
                        invoice.periodEnd());
        int currentWatermarkFailures = countIncompleteWatermarks(invoice);
        int currentExceptions = countOpenExceptions(invoice.billingTenantId(), invoice.id(), false);
        int approvedUnstaged = number(gate.get("unstaged_charge_count"));
        int approvedWatermarkFailures = number(gate.get("source_watermark_failure_count"));
        int approvedExceptions = number(gate.get("material_exception_count"));
        boolean waived =
                gate.get("waiver_reason") != null
                        && !String.valueOf(gate.get("waiver_reason")).isBlank();
        if (!waived) {
            return currentUnstaged == 0 && currentWatermarkFailures == 0 && currentExceptions == 0;
        }
        return currentUnstaged <= approvedUnstaged
                && currentWatermarkFailures <= approvedWatermarkFailures
                && currentExceptions <= approvedExceptions;
    }

    private BillingInvoiceRecord requireDraftInvoice(long billingInvoiceId) {
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
        return invoice;
    }

    private int countIncompleteWatermarks(BillingInvoiceRecord invoice) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("tenant", invoice.billingTenantId());
        p.addValue("period_end", Timestamp.valueOf(invoice.periodEnd().atTime(23, 59, 59)));
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT CASE WHEN COUNT(*)=0 THEN 1 ELSE SUM(CASE "
                                + "WHEN status<>'COMPLETE' OR observed_through_at IS NULL OR observed_through_at<:period_end "
                                + "THEN 1 ELSE 0 END) END FROM billing_source_watermarks "
                                + "WHERE billing_tenant_id=:tenant",
                        p,
                        Integer.class);
        return count == null ? 0 : count;
    }

    private int countOpenExceptions(
            long billingTenantId, long billingInvoiceId, boolean criticalOnly) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("tenant", billingTenantId);
        p.addValue("invoice", billingInvoiceId);
        String severity =
                criticalOnly ? "severity='CRITICAL'" : "severity IN ('MATERIAL','CRITICAL')";
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_operational_exceptions WHERE billing_tenant_id=:tenant "
                                + "AND (billing_invoice_id=:invoice OR billing_invoice_id IS NULL) AND status='OPEN' AND "
                                + severity,
                        p,
                        Integer.class);
        return count == null ? 0 : count;
    }

    private int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
