package net.citotech.cito.billing.tax;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves an explicitly approved effective-dated billing tax rule and persists the exact rule,
 * rate and amount used by an invoice. No implicit zero-tax fallback is allowed: operators must
 * configure an approved zero-rate rule when zero tax is the legally correct outcome.
 */
@Service
public class BillingTaxService {
    private static final String DEFAULT_TAX_CODE = "STANDARD";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingTaxService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BillingTaxSnapshot calculateAndSnapshot(
            long billingInvoiceId,
            long billingTenantId,
            String currency,
            LocalDate periodEnd,
            BigDecimal taxableAmount) {
        if (taxableAmount == null || taxableAmount.signum() < 0) {
            throw new PaymentGatewayException("Billing invoice taxable amount must be non-negative");
        }

        Optional<BillingTaxSnapshot> existing = findSnapshot(billingInvoiceId);
        if (existing.isPresent()) {
            BillingTaxSnapshot snapshot = existing.get();
            if (snapshot.taxableAmount().compareTo(taxableAmount) != 0) {
                throw new PaymentGatewayException(
                        "Billing invoice tax snapshot already exists for a different taxable amount");
            }
            return snapshot;
        }

        TaxRule rule = resolveRule(billingTenantId, currency, periodEnd);
        BigDecimal taxAmount =
                taxableAmount.multiply(rule.rate()).setScale(4, RoundingMode.HALF_UP);
        BillingTaxSnapshot snapshot =
                new BillingTaxSnapshot(
                        rule.id(),
                        rule.taxCode(),
                        taxableAmount,
                        rule.rate(),
                        taxAmount,
                        currency);

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_invoice_id", billingInvoiceId);
        p.addValue("tax_rule_version_id", snapshot.taxRuleVersionId());
        p.addValue("tax_code", snapshot.taxCode());
        p.addValue("taxable_amount", snapshot.taxableAmount());
        p.addValue("tax_rate", snapshot.taxRate());
        p.addValue("tax_amount", snapshot.taxAmount());
        p.addValue("currency", snapshot.currency());
        jdbcTemplate.update(
                "INSERT INTO billing_invoice_tax_snapshots "
                        + "(billing_invoice_id, tax_rule_version_id, tax_code, taxable_amount, tax_rate, tax_amount, currency) "
                        + "VALUES (:billing_invoice_id, :tax_rule_version_id, :tax_code, :taxable_amount, :tax_rate, :tax_amount, :currency)",
                p);
        return snapshot;
    }

    public Optional<BillingTaxSnapshot> findSnapshot(long billingInvoiceId) {
        List<BillingTaxSnapshot> rows =
                jdbcTemplate.query(
                        "SELECT tax_rule_version_id, tax_code, taxable_amount, tax_rate, tax_amount, currency "
                                + "FROM billing_invoice_tax_snapshots WHERE billing_invoice_id=:billing_invoice_id",
                        new MapSqlParameterSource("billing_invoice_id", billingInvoiceId),
                        (rs, rowNum) ->
                                new BillingTaxSnapshot(
                                        rs.getLong("tax_rule_version_id"),
                                        rs.getString("tax_code"),
                                        rs.getBigDecimal("taxable_amount"),
                                        rs.getBigDecimal("tax_rate"),
                                        rs.getBigDecimal("tax_amount"),
                                        rs.getString("currency")));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private TaxRule resolveRule(long billingTenantId, String currency, LocalDate periodEnd) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("billing_tenant_id", billingTenantId);
        p.addValue("tax_code", DEFAULT_TAX_CODE);
        p.addValue("currency", currency);
        p.addValue("as_of", Timestamp.valueOf(periodEnd.atTime(23, 59, 59)));

        List<TaxRule> rows =
                jdbcTemplate.query(
                        "SELECT id, tax_code, rate FROM billing_tax_rule_versions "
                                + "WHERE status='APPROVED' AND tax_code=:tax_code AND currency=:currency "
                                + "AND effective_from <= :as_of AND (effective_to IS NULL OR effective_to > :as_of) "
                                + "AND (billing_tenant_id=:billing_tenant_id OR billing_tenant_id IS NULL) "
                                + "ORDER BY CASE WHEN billing_tenant_id=:billing_tenant_id THEN 0 ELSE 1 END, effective_from DESC LIMIT 1",
                        p,
                        (rs, rowNum) ->
                                new TaxRule(
                                        rs.getLong("id"),
                                        rs.getString("tax_code"),
                                        rs.getBigDecimal("rate")));
        if (rows.isEmpty()) {
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
        return rows.get(0);
    }

    private record TaxRule(long id, String taxCode, BigDecimal rate) {}
}
