package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.billing.tax.BillingTaxSnapshot;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JDBC access for periodic billing invoices and their governed financial lifecycle. */
@Repository
public class BillingInvoiceRepository {
    private static final String DEFAULT_TAX_CODE = "STANDARD";
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingInvoiceRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insertDraft(
            long billingTenantId,
            String invoiceNumber,
            String currency,
            LocalDate periodStart,
            LocalDate periodEnd) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("invoice_number", invoiceNumber);
        p.addValue("currency", currency);
        p.addValue("period_start", periodStart);
        p.addValue("period_end", periodEnd);
        jdbcTemplate.update(
                "INSERT INTO billing_invoices "
                        + "(billing_tenant_id, invoice_number, currency, period_start, period_end, status) "
                        + "VALUES (:billing_tenant_id, :invoice_number, :currency, :period_start, :period_end, 'DRAFT')",
                p);
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    public Optional<BillingInvoiceRecord> find(long billingInvoiceId) {
        List<BillingInvoiceRecord> rows =
                jdbcTemplate.query(
                        "SELECT id, billing_tenant_id, invoice_number, currency, period_start, period_end, "
                                + "status, subtotal_amount, tax_amount, total_amount, finalized_at, "
                                + "finalized_by, ledger_transaction_id "
                                + "FROM billing_invoices WHERE id = :id",
                        new MapSqlParameterSource("id", billingInvoiceId),
                        this::mapInvoice);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public BigDecimal findOutstandingAmount(long billingInvoiceId) {
        BigDecimal amount =
                jdbcTemplate.queryForObject(
                        "SELECT outstanding_amount FROM billing_invoices WHERE id=:id",
                        new MapSqlParameterSource("id", billingInvoiceId),
                        BigDecimal.class);
        return amount == null ? BigDecimal.ZERO : amount;
    }

    public List<UnstagedRatedCharge> findUnstagedCustomerCharges(
            long billingTenantId, String currency, LocalDate periodStart, LocalDate periodEnd) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("currency", currency);
        p.addValue("period_start", periodStart);
        p.addValue("period_end", periodEnd);
        return jdbcTemplate.query(
                "SELECT id, service_code, meter_code, rated_amount FROM billing_rated_charges "
                        + "WHERE billing_tenant_id = :billing_tenant_id AND currency = :currency "
                        + "AND charge_type = 'CUSTOMER_CHARGE' "
                        + "AND DATE(computed_at) BETWEEN :period_start AND :period_end "
                        + "AND id NOT IN (SELECT billing_rated_charge_id FROM billing_invoice_lines "
                        + "WHERE billing_rated_charge_id IS NOT NULL)",
                p,
                (rs, rowNum) ->
                        new UnstagedRatedCharge(
                                rs.getLong("id"),
                                rs.getString("service_code"),
                                rs.getString("meter_code"),
                                rs.getBigDecimal("rated_amount")));
    }

    public int countUnstagedCustomerCharges(
            long billingTenantId, String currency, LocalDate periodStart, LocalDate periodEnd) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("currency", currency);
        p.addValue("period_start", periodStart);
        p.addValue("period_end", periodEnd);
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_rated_charges "
                                + "WHERE billing_tenant_id = :billing_tenant_id AND currency = :currency "
                                + "AND charge_type = 'CUSTOMER_CHARGE' "
                                + "AND DATE(computed_at) BETWEEN :period_start AND :period_end "
                                + "AND id NOT IN (SELECT billing_rated_charge_id FROM billing_invoice_lines "
                                + "WHERE billing_rated_charge_id IS NOT NULL)",
                        p,
                        Integer.class);
        return count == null ? 0 : count;
    }

    public void insertLine(
            long billingInvoiceId,
            Long billingRatedChargeId,
            String description,
            BigDecimal amount,
            String currency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("billing_rated_charge_id", billingRatedChargeId);
        p.addValue("description", description);
        p.addValue("amount", amount);
        p.addValue("currency", currency);
        jdbcTemplate.update(
                "INSERT INTO billing_invoice_lines "
                        + "(billing_invoice_id, billing_rated_charge_id, description, amount, currency) "
                        + "VALUES (:billing_invoice_id, :billing_rated_charge_id, :description, :amount, :currency)",
                p);
    }

    public BigDecimal sumLineAmounts(long billingInvoiceId) {
        BigDecimal sum =
                jdbcTemplate.queryForObject(
                        "SELECT COALESCE(SUM(amount), 0) FROM billing_invoice_lines "
                                + "WHERE billing_invoice_id = :billing_invoice_id",
                        new MapSqlParameterSource("billing_invoice_id", billingInvoiceId),
                        BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    public void updateTotals(
            long billingInvoiceId, BigDecimal subtotalAmount, BigDecimal totalAmount) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("subtotal_amount", subtotalAmount);
        p.addValue("total_amount", totalAmount);
        jdbcTemplate.update(
                "UPDATE billing_invoices SET subtotal_amount=:subtotal_amount, total_amount=:total_amount "
                        + "WHERE id=:id AND status='DRAFT'",
                p);
    }

    public BillingTaxSnapshot calculateAndSnapshotTax(
            long billingInvoiceId,
            long billingTenantId,
            String currency,
            LocalDate periodEnd,
            BigDecimal taxableAmount) {
        List<BillingTaxSnapshot> existing =
                jdbcTemplate.query(
                        "SELECT tax_rule_version_id,tax_code,taxable_amount,tax_rate,tax_amount,currency "
                                + "FROM billing_invoice_tax_snapshots WHERE billing_invoice_id=:id",
                        new MapSqlParameterSource("id", billingInvoiceId),
                        (rs, rowNum) ->
                                new BillingTaxSnapshot(
                                        rs.getLong("tax_rule_version_id"),
                                        rs.getString("tax_code"),
                                        rs.getBigDecimal("taxable_amount"),
                                        rs.getBigDecimal("tax_rate"),
                                        rs.getBigDecimal("tax_amount"),
                                        rs.getString("currency")));
        if (!existing.isEmpty()) {
            BillingTaxSnapshot snapshot = existing.get(0);
            if (snapshot.taxableAmount().compareTo(taxableAmount) != 0) {
                throw new PaymentGatewayException(
                        "Billing invoice tax snapshot already exists for a different taxable amount");
            }
            return snapshot;
        }

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("tax_code", DEFAULT_TAX_CODE);
        p.addValue("currency", currency);
        p.addValue("as_of", Timestamp.valueOf(periodEnd.atTime(23, 59, 59)));
        List<BillingTaxSnapshot> rules =
                jdbcTemplate.query(
                        "SELECT id,tax_code,rate FROM billing_tax_rule_versions "
                                + "WHERE status='APPROVED' AND tax_code=:tax_code AND currency=:currency "
                                + "AND effective_from<=:as_of AND (effective_to IS NULL OR effective_to>:as_of) "
                                + "AND (billing_tenant_id=:billing_tenant_id OR billing_tenant_id IS NULL) "
                                + "ORDER BY CASE WHEN billing_tenant_id=:billing_tenant_id THEN 0 ELSE 1 END,effective_from DESC LIMIT 1",
                        p,
                        (rs, rowNum) -> {
                            BigDecimal rate = rs.getBigDecimal("rate");
                            BigDecimal taxAmount =
                                    taxableAmount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
                            return new BillingTaxSnapshot(
                                    rs.getLong("id"),
                                    rs.getString("tax_code"),
                                    taxableAmount,
                                    rate,
                                    taxAmount,
                                    currency);
                        });
        if (rules.isEmpty()) {
            throw new PaymentGatewayException(
                    "No approved billing tax rule for tenant "
                            + billingTenantId
                            + ", currency "
                            + currency
                            + ", tax code "
                            + DEFAULT_TAX_CODE
                            + " as of "
                            + periodEnd);
        }
        BillingTaxSnapshot snapshot = rules.get(0);
        MapSqlParameterSource s = new MapSqlParameterSource();
        s.addValue("billing_invoice_id", billingInvoiceId);
        s.addValue("tax_rule_version_id", snapshot.taxRuleVersionId());
        s.addValue("tax_code", snapshot.taxCode());
        s.addValue("taxable_amount", snapshot.taxableAmount());
        s.addValue("tax_rate", snapshot.taxRate());
        s.addValue("tax_amount", snapshot.taxAmount());
        s.addValue("currency", snapshot.currency());
        jdbcTemplate.update(
                "INSERT INTO billing_invoice_tax_snapshots "
                        + "(billing_invoice_id,tax_rule_version_id,tax_code,taxable_amount,tax_rate,tax_amount,currency) "
                        + "VALUES (:billing_invoice_id,:tax_rule_version_id,:tax_code,:taxable_amount,:tax_rate,:tax_amount,:currency)",
                s);
        return snapshot;
    }

    public void updateTaxAndTotals(
            long billingInvoiceId,
            BigDecimal subtotalAmount,
            BigDecimal taxAmount,
            BigDecimal totalAmount) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("subtotal_amount", subtotalAmount);
        p.addValue("tax_amount", taxAmount);
        p.addValue("total_amount", totalAmount);
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_invoices SET subtotal_amount=:subtotal_amount,tax_amount=:tax_amount, "
                                + "total_amount=:total_amount WHERE id=:id AND status='DRAFT'",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Billing invoice is no longer DRAFT: " + billingInvoiceId);
        }
    }

    public int finalizeInvoice(
            long billingInvoiceId, String finalizedBy, long ledgerTransactionId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("finalized_by", finalizedBy);
        p.addValue("ledger_transaction_id", ledgerTransactionId);
        return jdbcTemplate.update(
                "UPDATE billing_invoices SET status='FINALIZED',finalized_at=CURRENT_TIMESTAMP, "
                        + "finalized_by=:finalized_by,ledger_transaction_id=:ledger_transaction_id, "
                        + "outstanding_amount=total_amount WHERE id=:id AND status='DRAFT'",
                p);
    }

    public int markDelivered(long billingInvoiceId, String deliveredBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("delivered_by", deliveredBy);
        return jdbcTemplate.update(
                "UPDATE billing_invoices SET delivered_at=COALESCE(delivered_at,CURRENT_TIMESTAMP), "
                        + "delivered_by=COALESCE(delivered_by,:delivered_by) WHERE id=:id AND status='FINALIZED'",
                p);
    }

    public int markVoid(long billingInvoiceId, String voidedBy, String reason) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("voided_by", voidedBy);
        p.addValue("void_reason", reason);
        return jdbcTemplate.update(
                "UPDATE billing_invoices SET status='VOID',voided_at=CURRENT_TIMESTAMP,voided_by=:voided_by, "
                        + "void_reason=:void_reason,outstanding_amount=0 WHERE id=:id AND status='FINALIZED'",
                p);
    }

    public int closeIfSettled(long billingInvoiceId, String closedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("closed_by", closedBy);
        return jdbcTemplate.update(
                "UPDATE billing_invoices SET closed_at=CURRENT_TIMESTAMP,closed_by=:closed_by "
                        + "WHERE id=:id AND status IN ('FINALIZED','VOID') AND outstanding_amount=0 AND closed_at IS NULL",
                p);
    }

    public boolean paymentAllocationExists(long billingInvoiceId, String paymentReference) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("payment_reference", paymentReference);
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_payment_allocations WHERE billing_invoice_id=:billing_invoice_id "
                                + "AND payment_reference=:payment_reference",
                        p,
                        Integer.class);
        return count != null && count > 0;
    }

    public void insertPaymentAllocation(
            long billingTenantId,
            long billingInvoiceId,
            String paymentReference,
            BigDecimal amount,
            String currency,
            long ledgerTransactionId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("payment_reference", paymentReference);
        p.addValue("amount", amount);
        p.addValue("currency", currency);
        p.addValue("ledger_transaction_id", ledgerTransactionId);
        jdbcTemplate.update(
                "INSERT INTO billing_payment_allocations "
                        + "(billing_tenant_id,billing_invoice_id,payment_reference,amount,currency,ledger_transaction_id) "
                        + "VALUES (:billing_tenant_id,:billing_invoice_id,:payment_reference,:amount,:currency,:ledger_transaction_id)",
                p);
    }

    public void reduceOutstanding(long billingInvoiceId, BigDecimal amount) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("amount", amount);
        jdbcTemplate.update(
                "UPDATE billing_invoices SET outstanding_amount=GREATEST(outstanding_amount-:amount,0) "
                        + "WHERE id=:id AND status='FINALIZED'",
                p);
    }

    public void insertPostedCreditNote(
            long billingTenantId,
            long billingInvoiceId,
            String creditNoteNumber,
            String currency,
            BigDecimal amount,
            String reason,
            long ledgerTransactionId,
            String issuedBy,
            String approvedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("credit_note_number", creditNoteNumber);
        p.addValue("currency", currency);
        p.addValue("amount", amount);
        p.addValue("reason", reason);
        p.addValue("ledger_transaction_id", ledgerTransactionId);
        p.addValue("issued_by", issuedBy);
        p.addValue("approved_by", approvedBy);
        jdbcTemplate.update(
                "INSERT INTO billing_credit_notes "
                        + "(billing_tenant_id,billing_invoice_id,credit_note_number,currency,amount,reason,status,ledger_transaction_id,issued_by,approved_by,approved_at) "
                        + "VALUES (:billing_tenant_id,:billing_invoice_id,:credit_note_number,:currency,:amount,:reason,'POSTED',:ledger_transaction_id,:issued_by,:approved_by,CURRENT_TIMESTAMP)",
                p);
    }

    private BillingInvoiceRecord mapInvoice(ResultSet rs, int rowNum) throws SQLException {
        Timestamp finalizedAt = rs.getTimestamp("finalized_at");
        Object ledgerTransactionIdObj = rs.getObject("ledger_transaction_id");
        return new BillingInvoiceRecord(
                rs.getLong("id"),
                rs.getLong("billing_tenant_id"),
                rs.getString("invoice_number"),
                rs.getString("currency"),
                rs.getDate("period_start").toLocalDate(),
                rs.getDate("period_end").toLocalDate(),
                rs.getString("status"),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("tax_amount"),
                rs.getBigDecimal("total_amount"),
                finalizedAt == null ? null : finalizedAt.toInstant(),
                rs.getString("finalized_by"),
                ledgerTransactionIdObj == null ? null : rs.getLong("ledger_transaction_id"));
    }
}
