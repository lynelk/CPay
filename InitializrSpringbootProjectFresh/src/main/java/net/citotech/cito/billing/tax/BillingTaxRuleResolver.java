package net.citotech.cito.billing.tax;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves an approved tenant override before the global tax rule at the supplied business time.
 */
@Service
public class BillingTaxRuleResolver {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingTaxRuleResolver(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ResolvedTaxRule resolve(
            long billingTenantId, String taxCode, String currency, Instant asOf) {
        if (billingTenantId <= 0 || asOf == null || blank(taxCode) || blank(currency)) {
            throw new PaymentGatewayException(
                    "Tax resolution requires tenant, taxCode, currency and asOf");
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("tax_code", taxCode.trim().toUpperCase())
                        .addValue("currency", currency.trim().toUpperCase())
                        .addValue("as_of", Timestamp.from(asOf));
        List<ResolvedTaxRule> rows =
                jdbcTemplate.query(
                        "SELECT id,tax_code,currency,rate,billing_tenant_id,effective_from,effective_to "
                                + "FROM billing_tax_rule_versions WHERE status='APPROVED' "
                                + "AND tax_code=:tax_code AND currency=:currency "
                                + "AND effective_from<=:as_of AND (effective_to IS NULL OR effective_to>:as_of) "
                                + "AND (billing_tenant_id=:tenant OR billing_tenant_id IS NULL) "
                                + "ORDER BY CASE WHEN billing_tenant_id=:tenant THEN 0 ELSE 1 END,"
                                + "effective_from DESC LIMIT 1",
                        p,
                        (rs, rowNum) ->
                                new ResolvedTaxRule(
                                        rs.getLong("id"),
                                        rs.getString("tax_code"),
                                        rs.getString("currency"),
                                        rs.getBigDecimal("rate"),
                                        rs.getObject("billing_tenant_id") == null
                                                ? null
                                                : rs.getLong("billing_tenant_id"),
                                        rs.getTimestamp("effective_from").toInstant(),
                                        rs.getTimestamp("effective_to") == null
                                                ? null
                                                : rs.getTimestamp("effective_to").toInstant()));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "No approved billing tax rule for "
                            + taxCode.trim().toUpperCase()
                            + " in "
                            + currency.trim().toUpperCase()
                            + " at "
                            + asOf);
        }
        return rows.get(0);
    }

    public BillingTaxSnapshot calculate(
            ResolvedTaxRule rule, BigDecimal taxableAmount, String currency) {
        if (rule == null || taxableAmount == null || taxableAmount.signum() < 0) {
            throw new PaymentGatewayException(
                    "Tax calculation requires a resolved rule and amount");
        }
        if (!rule.currency().equalsIgnoreCase(currency)) {
            throw new PaymentGatewayException(
                    "Tax rule currency does not match taxable amount currency");
        }
        BigDecimal tax =
                taxableAmount.multiply(rule.rate()).setScale(4, java.math.RoundingMode.HALF_UP);
        return new BillingTaxSnapshot(
                rule.id(),
                rule.taxCode(),
                taxableAmount.setScale(4, java.math.RoundingMode.HALF_UP),
                rule.rate(),
                tax,
                rule.currency());
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record ResolvedTaxRule(
            long id,
            String taxCode,
            String currency,
            BigDecimal rate,
            Long billingTenantId,
            Instant effectiveFrom,
            Instant effectiveTo) {}
}
