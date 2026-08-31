package net.citotech.cito.billing.baas;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only merchant Billing Center queries. Every query carries the authenticated tenant. */
@Service
public class BillingBaasBillingCenterService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingBaasBillingCenterService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> invoices(BillingBaasContext context, String status, int limit) {
        requireContext(context);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("status", blank(status) ? null : status.trim().toUpperCase())
                        .addValue("limit", Math.max(1, Math.min(limit, 200)));
        String statusFilter = blank(status) ? "" : " AND status=:status";
        return jdbcTemplate.queryForList(
                "SELECT id,invoice_number AS invoiceNumber,currency,period_start AS periodStart,"
                        + "period_end AS periodEnd,status,subtotal_amount AS subtotalAmount,tax_amount AS taxAmount,"
                        + "total_amount AS totalAmount,outstanding_amount AS outstandingAmount,finalized_at AS finalizedAt,"
                        + "delivered_at AS deliveredAt,closed_at AS closedAt,created_at AS createdAt "
                        + "FROM billing_invoices WHERE billing_tenant_id=:tenant"
                        + statusFilter
                        + " ORDER BY id DESC LIMIT :limit",
                p);
    }

    public Map<String, Object> invoice(BillingBaasContext context, String invoiceNumber) {
        requireContext(context);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("invoice", required(invoiceNumber, "invoiceNumber"));
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,invoice_number AS invoiceNumber,currency,period_start AS periodStart,"
                                + "period_end AS periodEnd,status,subtotal_amount AS subtotalAmount,tax_amount AS taxAmount,"
                                + "total_amount AS totalAmount,outstanding_amount AS outstandingAmount,finalized_at AS finalizedAt,"
                                + "finalized_by AS finalizedBy,delivered_at AS deliveredAt,voided_at AS voidedAt,"
                                + "void_reason AS voidReason,closed_at AS closedAt,ledger_transaction_id AS ledgerTransactionId,"
                                + "created_at AS createdAt FROM billing_invoices "
                                + "WHERE billing_tenant_id=:tenant AND invoice_number=:invoice LIMIT 1",
                        p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Billing invoice was not found for this tenant");
        }
        Map<String, Object> invoice = new LinkedHashMap<>(rows.get(0));
        long invoiceId = ((Number) invoice.get("id")).longValue();
        MapSqlParameterSource detail =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("invoice_id", invoiceId);
        invoice.put(
                "lines",
                jdbcTemplate.queryForList(
                        "SELECT il.id,il.billing_rated_charge_id AS ratedChargeId,il.description,il.amount,il.currency,"
                                + "il.created_at AS createdAt FROM billing_invoice_lines il "
                                + "JOIN billing_invoices i ON i.id=il.billing_invoice_id "
                                + "WHERE i.id=:invoice_id AND i.billing_tenant_id=:tenant ORDER BY il.id",
                        detail));
        invoice.put(
                "creditNotes",
                jdbcTemplate.queryForList(
                        "SELECT id,credit_note_number AS creditNoteNumber,currency,amount,reason,status,"
                                + "issued_by AS issuedBy,approved_by AS approvedBy,approved_at AS approvedAt,created_at AS createdAt "
                                + "FROM billing_credit_notes WHERE billing_invoice_id=:invoice_id "
                                + "AND billing_tenant_id=:tenant ORDER BY id",
                        detail));
        invoice.put(
                "paymentAllocations",
                jdbcTemplate.queryForList(
                        "SELECT id,payment_reference AS paymentReference,amount,currency,ledger_transaction_id AS ledgerTransactionId,"
                                + "created_at AS createdAt FROM billing_payment_allocations "
                                + "WHERE billing_invoice_id=:invoice_id AND billing_tenant_id=:tenant ORDER BY id",
                        detail));
        invoice.put(
                "taxSnapshot",
                jdbcTemplate.queryForList(
                        "SELECT ts.tax_rule_version_id AS taxRuleVersionId,ts.tax_code AS taxCode,"
                                + "ts.taxable_amount AS taxableAmount,ts.tax_rate AS taxRate,ts.tax_amount AS taxAmount,"
                                + "ts.currency,ts.captured_at AS capturedAt FROM billing_invoice_tax_snapshots ts "
                                + "JOIN billing_invoices i ON i.id=ts.billing_invoice_id "
                                + "WHERE i.id=:invoice_id AND i.billing_tenant_id=:tenant LIMIT 1",
                        detail));
        return invoice;
    }

    public Map<String, Object> quota(BillingBaasContext context) {
        requireContext(context);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("project", context.developerProjectId())
                        .addValue("environment", context.environment());
        List<Map<String, Object>> policies =
                jdbcTemplate.queryForList(
                        "SELECT requests_per_minute AS requestsPerMinute,usage_events_per_day AS usageEventsPerDay,"
                                + "max_batch_size AS maxBatchSize FROM billing_api_quota_policies "
                                + "WHERE billing_tenant_id=:tenant AND environment=:environment AND status='ACTIVE' "
                                + "AND (developer_project_id=:project OR developer_project_id IS NULL) "
                                + "ORDER BY CASE WHEN developer_project_id=:project THEN 0 ELSE 1 END LIMIT 1",
                        p);
        Map<String, Object> policy =
                policies.isEmpty()
                        ? Map.of(
                                "requestsPerMinute", 300,
                                "usageEventsPerDay", 100000L,
                                "maxBatchSize", 1000)
                        : policies.get(0);
        Long requestsLastMinute =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM developer_api_request_log WHERE project_id=:project "
                                + "AND environment=:environment AND created_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 1 MINUTE)",
                        p,
                        Long.class);
        Long usageToday =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_usage_events WHERE billing_tenant_id=:tenant "
                                + "AND created_at>=UTC_DATE()",
                        p,
                        Long.class);
        Map<String, Object> result = new LinkedHashMap<>(policy);
        result.put("requestsUsedLastMinute", requestsLastMinute == null ? 0L : requestsLastMinute);
        result.put("usageEventsUsedToday", usageToday == null ? 0L : usageToday);
        result.put("environment", context.environment());
        return result;
    }

    public List<Map<String, Object>> entitlements(BillingBaasContext context) {
        requireContext(context);
        return jdbcTemplate.queryForList(
                "SELECT s.subscription_reference AS subscriptionReference,s.service_code AS serviceCode,"
                        + "s.plan_code AS planCode,s.status AS subscriptionStatus,g.entitlement_code AS entitlementCode,"
                        + "g.limit_quantity AS limitQuantity,g.valid_from AS validFrom,g.valid_to AS validTo,g.status "
                        + "FROM billing_entitlement_grants g JOIN billing_subscriptions s "
                        + "ON s.id=g.billing_subscription_id AND s.billing_tenant_id=g.billing_tenant_id "
                        + "WHERE g.billing_tenant_id=:tenant ORDER BY s.service_code,g.entitlement_code",
                new MapSqlParameterSource("tenant", context.billingTenantId()));
    }

    public List<Map<String, Object>> catalog(BillingBaasContext context, Instant asOf) {
        requireContext(context);
        Instant effective = asOf == null ? Instant.now() : asOf;
        return jdbcTemplate.queryForList(
                "SELECT s.service_code AS serviceCode,s.service_name AS serviceName,m.meter_code AS meterCode,"
                        + "m.meter_name AS meterName,m.aggregation_type AS aggregationType,mv.version_no AS versionNo,"
                        + "mv.dimension_keys AS dimensionKeys,mv.effective_from AS effectiveFrom,mv.effective_to AS effectiveTo "
                        + "FROM billing_service_catalog s JOIN billing_meters m ON m.service_code=s.service_code "
                        + "JOIN billing_meter_versions mv ON mv.meter_id=m.id "
                        + "WHERE s.service_status='ACTIVE' AND m.meter_status='ACTIVE' "
                        + "AND mv.effective_from<=:as_of AND (mv.effective_to IS NULL OR mv.effective_to>:as_of) "
                        + "ORDER BY s.service_code,m.meter_code,mv.version_no DESC",
                new MapSqlParameterSource("as_of", Timestamp.from(effective)));
    }

    private void requireContext(BillingBaasContext context) {
        if (context == null || context.billingTenantId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS tenant context is required");
        }
    }

    private String required(String value, String field) {
        if (blank(value)) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
