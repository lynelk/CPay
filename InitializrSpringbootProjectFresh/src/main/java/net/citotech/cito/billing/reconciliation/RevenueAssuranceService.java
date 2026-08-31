package net.citotech.cito.billing.reconciliation;

import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Tenant-scoped revenue assurance over the canonical billing chain. This service intentionally
 * reports exceptions rather than repairing money automatically. Resolution remains an audited
 * operational/finance action.
 */
@Service
public class RevenueAssuranceService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RevenueAssuranceService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RevenueAssuranceSummary summarize(long billingTenantId) {
        if (billingTenantId <= 0) {
            throw new PaymentGatewayException("Billing tenant is required for revenue assurance");
        }
        MapSqlParameterSource p = new MapSqlParameterSource("tenant", billingTenantId);

        long watermarks =
                count(
                        "SELECT COUNT(*) FROM billing_source_watermarks "
                                + "WHERE billing_tenant_id=:tenant AND status<>'COMPLETE'",
                        p);
        long materialExceptions =
                count(
                        "SELECT COUNT(*) FROM billing_operational_exceptions "
                                + "WHERE billing_tenant_id=:tenant AND status='OPEN' "
                                + "AND severity IN ('MATERIAL','CRITICAL')",
                        p);
        long unratedUsage =
                count(
                        "SELECT COUNT(*) FROM billing_usage_events ue "
                                + "LEFT JOIN billing_rated_charges rc ON rc.billing_tenant_id=ue.billing_tenant_id "
                                + "AND rc.source_reference=ue.source_reference AND rc.service_code=ue.service_code "
                                + "AND rc.meter_code=ue.meter_code AND rc.charge_type='CUSTOMER_CHARGE' "
                                + "WHERE ue.billing_tenant_id=:tenant AND rc.id IS NULL",
                        p);
        long uninvoicedCharges =
                count(
                        "SELECT COUNT(*) FROM billing_rated_charges rc "
                                + "LEFT JOIN billing_invoice_lines il ON il.billing_rated_charge_id=rc.id "
                                + "WHERE rc.billing_tenant_id=:tenant AND rc.charge_type='CUSTOMER_CHARGE' "
                                + "AND il.id IS NULL",
                        p);

        Map<String, Object> margin =
                jdbcTemplate.queryForMap(
                        "SELECT COUNT(*) AS negative_margin_count,"
                                + "COALESCE(SUM(pc.rated_amount-cc.rated_amount),0) AS exposure "
                                + "FROM billing_rated_charges cc "
                                + "JOIN billing_rated_charges pc ON pc.billing_tenant_id=cc.billing_tenant_id "
                                + "AND pc.source_reference=cc.source_reference AND pc.service_code=cc.service_code "
                                + "AND pc.meter_code=cc.meter_code AND pc.charge_type='PROVIDER_COST' "
                                + "WHERE cc.billing_tenant_id=:tenant AND cc.charge_type='CUSTOMER_CHARGE' "
                                + "AND pc.rated_amount>cc.rated_amount",
                        p);
        long negativeMarginCount = ((Number) margin.get("negative_margin_count")).longValue();
        BigDecimal exposure = (BigDecimal) margin.get("exposure");

        return new RevenueAssuranceSummary(
                billingTenantId,
                watermarks,
                materialExceptions,
                unratedUsage,
                uninvoicedCharges,
                negativeMarginCount,
                exposure == null ? BigDecimal.ZERO : exposure);
    }

    private long count(String sql, MapSqlParameterSource parameters) {
        Long result = jdbcTemplate.queryForObject(sql, parameters, Long.class);
        return result == null ? 0L : result;
    }
}
