package net.citotech.cito.billing.export;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only join across {@code billing_usage_events} (V40), {@code billing_rated_charges} (V44),
 * {@code billing_invoice_lines}/{@code billing_invoices} (V47), and {@code ledger_entries} (V7) -
 * infra only, no controller/export format (FOCUS-style export is a much later phase). Combines what
 * would otherwise be a repository+service split into one class since it doesn't own any of the four
 * tables it joins.
 *
 * <p>{@link #traceByInvoice}'s inner joins mean a manual-adjustment line ({@code
 * billing_invoice_lines.billing_rated_charge_id IS NULL} - not producible by anything today, that
 * is credit-note/allocation territory, still unscoped) is silently excluded from the result, not an
 * error.
 */
@Service
public class BillingTraceChainService {
    private static final String SELECT_COLUMNS =
            "ue.id AS usage_event_id, rc.id AS rated_charge_id, rc.rated_amount AS rated_charge_amount, "
                    + "bil.id AS invoice_line_id, bil.amount AS invoice_line_amount, "
                    + "bi.id AS billing_invoice_id, bi.invoice_number, bi.status AS invoice_status, "
                    + "bi.ledger_transaction_id, le.id AS ledger_entry_id, "
                    + "le.entry_direction AS ledger_entry_direction, le.amount AS ledger_entry_amount ";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingTraceChainService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Traces one usage event's full chain, including whatever ledger entries its invoice posted.
     */
    public List<BillingTraceChainRow> traceBySourceReference(
            long billingTenantId, String sourceReference) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("source_reference", sourceReference);
        return jdbcTemplate.query(
                "SELECT "
                        + SELECT_COLUMNS
                        + "FROM billing_usage_events ue "
                        + "JOIN billing_rated_charges rc ON rc.source_reference = ue.source_reference "
                        + "AND rc.billing_tenant_id = ue.billing_tenant_id "
                        + "JOIN billing_invoice_lines bil ON bil.billing_rated_charge_id = rc.id "
                        + "JOIN billing_invoices bi ON bi.id = bil.billing_invoice_id "
                        + "LEFT JOIN ledger_entries le ON le.ledger_transaction_id = bi.ledger_transaction_id "
                        + "WHERE ue.billing_tenant_id = :billing_tenant_id "
                        + "AND ue.source_reference = :source_reference "
                        + "ORDER BY le.id",
                p,
                this::mapRow);
    }

    /**
     * Traces every rated-charge-backed line on one invoice back to its usage event, plus the ledger
     * entries the invoice posted once finalized.
     */
    public List<BillingTraceChainRow> traceByInvoice(long billingInvoiceId) {
        MapSqlParameterSource p = new MapSqlParameterSource("billing_invoice_id", billingInvoiceId);
        return jdbcTemplate.query(
                "SELECT "
                        + SELECT_COLUMNS
                        + "FROM billing_invoices bi "
                        + "JOIN billing_invoice_lines bil ON bil.billing_invoice_id = bi.id "
                        + "JOIN billing_rated_charges rc ON rc.id = bil.billing_rated_charge_id "
                        + "JOIN billing_usage_events ue ON ue.source_reference = rc.source_reference "
                        + "AND ue.billing_tenant_id = rc.billing_tenant_id "
                        + "LEFT JOIN ledger_entries le ON le.ledger_transaction_id = bi.ledger_transaction_id "
                        + "WHERE bi.id = :billing_invoice_id "
                        + "ORDER BY le.id",
                p,
                this::mapRow);
    }

    private BillingTraceChainRow mapRow(ResultSet rs, int rowNum) throws SQLException {
        Object ledgerTxObj = rs.getObject("ledger_transaction_id");
        Object ledgerEntryObj = rs.getObject("ledger_entry_id");
        return new BillingTraceChainRow(
                rs.getLong("usage_event_id"),
                rs.getLong("rated_charge_id"),
                rs.getBigDecimal("rated_charge_amount"),
                rs.getLong("invoice_line_id"),
                rs.getBigDecimal("invoice_line_amount"),
                rs.getLong("billing_invoice_id"),
                rs.getString("invoice_number"),
                rs.getString("invoice_status"),
                ledgerTxObj == null ? null : rs.getLong("ledger_transaction_id"),
                ledgerEntryObj == null ? null : rs.getLong("ledger_entry_id"),
                rs.getString("ledger_entry_direction"),
                rs.getBigDecimal("ledger_entry_amount"));
    }
}
