package net.citotech.cito.billing.export;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Exports tenant-scoped Cito usage using the FOCUS 1.4 Cost and Usage column model. The export
 * includes all unconditional mandatory Cost and Usage columns and the usage/unit-price columns that
 * apply to Cito's metered, versioned price-book model. Provider cost remains a separate Cito custom
 * column and never replaces customer BilledCost.
 */
@Service
public class FocusExportService {
    public static final String FOCUS_VERSION = "1.4";

    private static final String[] HEADERS = {
        "BilledCost",
        "BillingAccountId",
        "BillingAccountName",
        "BillingCurrency",
        "BillingPeriodEnd",
        "BillingPeriodStart",
        "ChargeCategory",
        "ChargeClass",
        "ChargeDescription",
        "ChargeFrequency",
        "ChargePeriodEnd",
        "ChargePeriodStart",
        "ConsumedQuantity",
        "ConsumedUnit",
        "ContractedCost",
        "ContractedUnitPrice",
        "EffectiveCost",
        "HostProviderName",
        "InvoiceIssuerName",
        "ListCost",
        "ListUnitPrice",
        "PricingCategory",
        "PricingCurrency",
        "PricingQuantity",
        "PricingUnit",
        "ResourceId",
        "ResourceName",
        "ServiceProviderName",
        "ServiceCategory",
        "ServiceName",
        "SkuId",
        "SkuMeter",
        "SkuPriceId",
        "Tags",
        "x_CitoProviderCost",
        "x_CitoCustomerPriceBookVersionId",
        "x_CitoProviderPriceBookVersionId",
        "x_CitoUsageEventId"
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public FocusExportService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<FocusCostUsageRow> rows(long billingTenantId, Instant from, Instant to) {
        if (billingTenantId <= 0) {
            throw new PaymentGatewayException(
                    "Authenticated billing tenant is required for export");
        }
        if (from == null || to == null || !to.isAfter(from)) {
            throw new PaymentGatewayException("FOCUS export requires a valid from/to interval");
        }

        MapSqlParameterSource parameters =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("from", Timestamp.from(from))
                        .addValue("to", Timestamp.from(to));

        return jdbcTemplate.query(
                "SELECT ue.id AS usage_event_id,ue.service_code,ue.meter_code,ue.event_time,"
                        + "ue.quantity,ue.dimensions,ue.source_reference,cc.currency,"
                        + "cc.rated_amount AS customer_charge,cc.price_book_version_id AS customer_price_version,"
                        + "pc.rated_amount AS provider_cost,pc.price_book_version_id AS provider_price_version,"
                        + "COALESCE(m.name,CONCAT('Cito tenant ',ue.billing_tenant_id)) AS billing_account_name "
                        + "FROM billing_usage_events ue "
                        + "JOIN billing_tenants bt ON bt.id=ue.billing_tenant_id "
                        + "LEFT JOIN merchants m ON m.id=bt.merchant_id "
                        + "JOIN billing_rated_charges cc ON cc.billing_tenant_id=ue.billing_tenant_id "
                        + "AND cc.source_reference=ue.source_reference AND cc.service_code=ue.service_code "
                        + "AND cc.meter_code=ue.meter_code AND cc.charge_type='CUSTOMER_CHARGE' "
                        + "LEFT JOIN billing_rated_charges pc ON pc.billing_tenant_id=ue.billing_tenant_id "
                        + "AND pc.source_reference=ue.source_reference AND pc.service_code=ue.service_code "
                        + "AND pc.meter_code=ue.meter_code AND pc.charge_type='PROVIDER_COST' "
                        + "WHERE ue.billing_tenant_id=:tenant AND ue.event_time>=:from AND ue.event_time<:to "
                        + "ORDER BY ue.event_time,ue.id",
                parameters,
                (rs, rowNum) -> {
                    Instant eventTime = rs.getTimestamp("event_time").toInstant();
                    Instant eventEnd = eventTime.plus(1, ChronoUnit.MICROS);
                    String serviceCode = rs.getString("service_code");
                    String meterCode = rs.getString("meter_code");
                    long usageEventId = rs.getLong("usage_event_id");
                    BigDecimal quantity = rs.getBigDecimal("quantity");
                    BigDecimal customerCharge = rs.getBigDecimal("customer_charge");
                    BigDecimal providerCost = rs.getBigDecimal("provider_cost");
                    long customerPriceVersion = rs.getLong("customer_price_version");
                    Object providerPriceVersion = rs.getObject("provider_price_version");
                    String sourceReference = rs.getString("source_reference");
                    BigDecimal unitPrice =
                            quantity == null || quantity.signum() == 0
                                    ? null
                                    : customerCharge.divide(quantity, 12, RoundingMode.HALF_UP);
                    return new FocusCostUsageRow(
                            customerCharge,
                            "cito:billing-tenant:" + billingTenantId,
                            rs.getString("billing_account_name"),
                            rs.getString("currency"),
                            to,
                            from,
                            "Usage",
                            null,
                            serviceCode + " / " + meterCode,
                            "Usage-Based",
                            eventEnd,
                            eventTime,
                            quantity,
                            meterCode,
                            customerCharge,
                            unitPrice,
                            customerCharge,
                            "Cito",
                            "Cito",
                            customerCharge,
                            unitPrice,
                            "Standard",
                            rs.getString("currency"),
                            quantity,
                            meterCode,
                            sourceReference,
                            sourceReference,
                            "Cito",
                            serviceCategory(serviceCode),
                            serviceCode,
                            serviceCode + ":" + meterCode,
                            meterCode,
                            "cito-price-book-version:" + customerPriceVersion,
                            rs.getString("dimensions"),
                            providerCost,
                            customerPriceVersion,
                            providerPriceVersion == null
                                    ? null
                                    : ((Number) providerPriceVersion).longValue(),
                            usageEventId);
                });
    }

    public String csv(long billingTenantId, Instant from, Instant to) {
        List<String> lines = new ArrayList<>();
        lines.add(String.join(",", HEADERS));
        for (FocusCostUsageRow row : rows(billingTenantId, from, to)) {
            lines.add(toCsv(row));
        }
        return String.join("\n", lines) + "\n";
    }

    private String toCsv(FocusCostUsageRow row) {
        return String.join(
                ",",
                csv(row.billedCost()),
                csv(row.billingAccountId()),
                csv(row.billingAccountName()),
                csv(row.billingCurrency()),
                csv(row.billingPeriodEnd()),
                csv(row.billingPeriodStart()),
                csv(row.chargeCategory()),
                csv(row.chargeClass()),
                csv(row.chargeDescription()),
                csv(row.chargeFrequency()),
                csv(row.chargePeriodEnd()),
                csv(row.chargePeriodStart()),
                csv(row.consumedQuantity()),
                csv(row.consumedUnit()),
                csv(row.contractedCost()),
                csv(row.contractedUnitPrice()),
                csv(row.effectiveCost()),
                csv(row.hostProviderName()),
                csv(row.invoiceIssuerName()),
                csv(row.listCost()),
                csv(row.listUnitPrice()),
                csv(row.pricingCategory()),
                csv(row.pricingCurrency()),
                csv(row.pricingQuantity()),
                csv(row.pricingUnit()),
                csv(row.resourceId()),
                csv(row.resourceName()),
                csv(row.serviceProviderName()),
                csv(row.serviceCategory()),
                csv(row.serviceName()),
                csv(row.skuId()),
                csv(row.skuMeter()),
                csv(row.skuPriceId()),
                csv(row.tags()),
                csv(row.x_CitoProviderCost()),
                csv(row.x_CitoCustomerPriceBookVersionId()),
                csv(row.x_CitoProviderPriceBookVersionId()),
                csv(row.x_CitoUsageEventId()));
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value instanceof Instant instant ? instant.toString() : String.valueOf(value);
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private String serviceCategory(String serviceCode) {
        String normalized = serviceCode == null ? "" : serviceCode.toUpperCase(Locale.ROOT);
        if (normalized.contains("IDENTITY")
                || normalized.contains("KYC")
                || normalized.contains("KYB")) {
            return "Identity";
        }
        if (normalized.contains("SMS")
                || normalized.contains("USSD")
                || normalized.contains("WHATSAPP")
                || normalized.contains("COMMUNICATION")) {
            return "Mobile";
        }
        if (normalized.contains("API") || normalized.contains("DEVELOPER")) {
            return "Developer Tools";
        }
        if (normalized.contains("AI")) {
            return "AI and Machine Learning";
        }
        if (normalized.contains("STORAGE")) {
            return "Storage";
        }
        if (normalized.contains("ANALYTIC")) {
            return "Analytics";
        }
        if (normalized.contains("INTEGRATION")) {
            return "Integration";
        }
        if (normalized.contains("WEBHOOK")) {
            return "Integration";
        }
        return "Other";
    }
}
