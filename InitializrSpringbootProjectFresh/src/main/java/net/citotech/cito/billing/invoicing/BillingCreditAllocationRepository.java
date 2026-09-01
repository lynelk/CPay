package net.citotech.cito.billing.invoicing;

import java.math.BigDecimal;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** Immutable tax/revenue allocation evidence for posted billing credit notes. */
@Repository
public class BillingCreditAllocationRepository {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingCreditAllocationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CreditTotals totalsForInvoice(long billingInvoiceId) {
        MapSqlParameterSource p = new MapSqlParameterSource("billing_invoice_id", billingInvoiceId);
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(gross_amount),0) AS gross_amount, "
                        + "COALESCE(SUM(revenue_amount),0) AS revenue_amount, "
                        + "COALESCE(SUM(tax_amount),0) AS tax_amount "
                        + "FROM billing_credit_note_allocations WHERE billing_invoice_id=:billing_invoice_id",
                p,
                (rs, rowNum) ->
                        new CreditTotals(
                                MoneyAmount.normalize(rs.getBigDecimal("gross_amount")),
                                MoneyAmount.normalize(rs.getBigDecimal("revenue_amount")),
                                MoneyAmount.normalize(rs.getBigDecimal("tax_amount"))));
    }

    public void insert(
            long billingTenantId,
            long billingInvoiceId,
            String creditNoteNumber,
            BigDecimal grossAmount,
            BigDecimal revenueAmount,
            BigDecimal taxAmount,
            String currency) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("billing_tenant_id", billingTenantId)
                        .addValue("billing_invoice_id", billingInvoiceId)
                        .addValue("credit_note_number", creditNoteNumber)
                        .addValue("gross_amount", MoneyAmount.normalize(grossAmount))
                        .addValue("revenue_amount", MoneyAmount.normalize(revenueAmount))
                        .addValue("tax_amount", MoneyAmount.normalize(taxAmount))
                        .addValue("currency", currency.trim().toUpperCase());
        jdbcTemplate.update(
                "INSERT INTO billing_credit_note_allocations "
                        + "(billing_tenant_id,billing_invoice_id,credit_note_number,gross_amount,revenue_amount,tax_amount,currency) "
                        + "VALUES (:billing_tenant_id,:billing_invoice_id,:credit_note_number,:gross_amount,:revenue_amount,:tax_amount,:currency)",
                p);
    }

    public record CreditTotals(
            BigDecimal grossAmount, BigDecimal revenueAmount, BigDecimal taxAmount) {}
}
