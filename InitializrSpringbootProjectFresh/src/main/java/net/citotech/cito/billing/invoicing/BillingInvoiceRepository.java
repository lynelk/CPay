package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC access to {@code billing_invoices}/{@code billing_invoice_lines} (Flyway {@code V47}), plus
 * the one cross-table read {@link BillingInvoiceService} needs into {@code billing_rated_charges}
 * (Flyway {@code V44}) to find charges not yet staged onto any invoice.
 */
@Repository
public class BillingInvoiceRepository {
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

    /**
     * {@code CUSTOMER_CHARGE} rated charges (never {@code PROVIDER_COST} - that is what CPay owes a
     * provider, not what a tenant owes CPay) for this tenant/currency/period, computed but not yet
     * present on any {@code billing_invoice_lines} row. MVP query, not performance-tuned: the
     * exclusion subquery scans all of {@code billing_invoice_lines} rather than being scoped to a
     * tenant, matching this billing module's other MVP-labeled reads (e.g. {@code
     * MeterAggregationService}).
     */
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

    /**
     * Same filter as {@link #findUnstagedCustomerCharges}, as a count only - used by {@code
     * BillingCompletenessGateService}, which only needs to know how many charges are still
     * unstaged, not their details. The WHERE clause is intentionally duplicated rather than shared
     * with {@link #findUnstagedCustomerCharges} to keep each query self-contained and simple.
     */
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
                "UPDATE billing_invoices SET subtotal_amount = :subtotal_amount, "
                        + "total_amount = :total_amount WHERE id = :id",
                p);
    }

    /**
     * Flips a DRAFT invoice to FINALIZED, recording who finalized it and the ledger transaction the
     * finalize posted. The {@code status='DRAFT'} guard makes this safe under a race: if two {@code
     * BillingInvoiceService#finalizeInvoice} calls for the same invoice interleave, only one UPDATE
     * affects a row - the caller that gets {@code 0} must treat that as a failure, not silently
     * succeed.
     */
    public int finalizeInvoice(
            long billingInvoiceId, String finalizedBy, long ledgerTransactionId) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", billingInvoiceId);
        p.addValue("finalized_by", finalizedBy);
        p.addValue("ledger_transaction_id", ledgerTransactionId);
        return jdbcTemplate.update(
                "UPDATE billing_invoices SET status='FINALIZED', finalized_at=CURRENT_TIMESTAMP, "
                        + "finalized_by=:finalized_by, ledger_transaction_id=:ledger_transaction_id "
                        + "WHERE id=:id AND status='DRAFT'",
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
