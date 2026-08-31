package net.citotech.cito.billing.baas;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.billing.usage.UsageEvent;
import net.citotech.cito.billing.usage.UsageEventRepository;
import net.citotech.cito.billing.usage.UsageGatewayService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BillingBaasUsageService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final UsageGatewayService usageGatewayService;
    private final UsageEventRepository usageEventRepository;
    private final ObjectMapper objectMapper;

    public BillingBaasUsageService(
            NamedParameterJdbcTemplate jdbcTemplate,
            UsageGatewayService usageGatewayService,
            UsageEventRepository usageEventRepository,
            ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.usageGatewayService = usageGatewayService;
        this.usageEventRepository = usageEventRepository;
        this.objectMapper = objectMapper;
    }

    public UsageEvent ingest(
            BillingBaasContext context,
            String serviceCode,
            String meterCode,
            Instant eventTime,
            BigDecimal quantity,
            String currency,
            Map<String, String> dimensions,
            String sourceReference,
            String idempotencyKey) {
        requireContext(context);
        Instant time = eventTime == null ? Instant.now() : eventTime;
        if (time.isAfter(Instant.now().plusSeconds(300))) {
            throw new PaymentGatewayException("Usage event time cannot be more than five minutes in the future");
        }
        String service = required(serviceCode, "serviceCode").toUpperCase(Locale.ROOT);
        String meter = required(meterCode, "meterCode");
        BigDecimal qty = positive(quantity, "quantity");
        String ccy = blankToNull(currency);
        Map<String, String> dims = dimensions == null ? Map.of() : Map.copyOf(dimensions);

        validateMeter(service, meter, time, dims);
        enforceDailyQuota(context);
        UsageEvent event =
                usageGatewayService.recordUsage(
                        context.merchantId(),
                        service,
                        meter,
                        time,
                        qty,
                        ccy,
                        dims,
                        required(sourceReference, "sourceReference"),
                        required(idempotencyKey, "idempotencyKey"));
        if (event.billingTenantId() != context.billingTenantId()) {
            throw new PaymentGatewayException("Usage event resolved outside authenticated billing tenant");
        }
        return event;
    }

    public List<UsageEvent> list(
            BillingBaasContext context,
            Instant from,
            Instant to,
            String serviceCode,
            int limit) {
        requireContext(context);
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minusSeconds(30L * 24 * 3600) : from;
        if (!end.isAfter(start)) {
            throw new PaymentGatewayException("Usage query requires to after from");
        }
        return usageEventRepository.findForTenant(
                context.billingTenantId(),
                start,
                end,
                blankToNull(serviceCode) == null
                        ? null
                        : serviceCode.trim().toUpperCase(Locale.ROOT),
                limit);
    }

    public List<Map<String, Object>> summary(
            BillingBaasContext context, Instant from, Instant to) {
        requireContext(context);
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minusSeconds(30L * 24 * 3600) : from;
        if (!end.isAfter(start)) {
            throw new PaymentGatewayException("Usage summary requires to after from");
        }
        return jdbcTemplate.queryForList(
                "SELECT service_code AS serviceCode,meter_code AS meterCode,COUNT(*) AS eventCount,"
                        + "SUM(quantity) AS totalQuantity,MIN(event_time) AS firstEventAt,MAX(event_time) AS lastEventAt "
                        + "FROM billing_usage_events WHERE billing_tenant_id=:tenant "
                        + "AND event_time>=:from AND event_time<:to GROUP BY service_code,meter_code "
                        + "ORDER BY service_code,meter_code",
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("from", Timestamp.from(start))
                        .addValue("to", Timestamp.from(end)));
    }

    private void validateMeter(
            String serviceCode, String meterCode, Instant eventTime, Map<String, String> dimensions) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT mv.dimension_keys FROM billing_service_catalog s "
                                + "JOIN billing_meters m ON m.service_code=s.service_code "
                                + "JOIN billing_meter_versions mv ON mv.meter_id=m.id "
                                + "WHERE s.service_code=:service AND s.service_status='ACTIVE' "
                                + "AND m.meter_code=:meter AND m.meter_status='ACTIVE' "
                                + "AND mv.effective_from<=:event_time "
                                + "AND (mv.effective_to IS NULL OR mv.effective_to>:event_time) "
                                + "ORDER BY mv.effective_from DESC,mv.version_no DESC LIMIT 2",
                        new MapSqlParameterSource()
                                .addValue("service", serviceCode)
                                .addValue("meter", meterCode)
                                .addValue("event_time", Timestamp.from(eventTime)));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Usage service/meter is not active at event time");
        }
        if (rows.size() > 1) {
            throw new PaymentGatewayException("Multiple billing meter versions are effective at event time");
        }
        Object json = rows.get(0).get("dimension_keys");
        if (json == null || dimensions.isEmpty()) {
            if (json == null && !dimensions.isEmpty()) {
                throw new PaymentGatewayException("Meter does not accept usage dimensions");
            }
            return;
        }
        try {
            Set<String> allowed =
                    new HashSet<>(
                            objectMapper.readValue(
                                    String.valueOf(json), new TypeReference<List<String>>() {}));
            Set<String> unsupported = new HashSet<>(dimensions.keySet());
            unsupported.removeAll(allowed);
            if (!unsupported.isEmpty()) {
                throw new PaymentGatewayException(
                        "Usage contains unsupported meter dimensions: " + unsupported);
            }
        } catch (PaymentGatewayException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentGatewayException("Billing meter dimension contract is invalid");
        }
    }

    private void enforceDailyQuota(BillingBaasContext context) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("project", context.developerProjectId())
                        .addValue("environment", context.environment());
        List<Long> limits =
                jdbcTemplate.query(
                        "SELECT usage_events_per_day FROM billing_api_quota_policies "
                                + "WHERE billing_tenant_id=:tenant AND environment=:environment AND status='ACTIVE' "
                                + "AND (developer_project_id=:project OR developer_project_id IS NULL) "
                                + "ORDER BY CASE WHEN developer_project_id=:project THEN 0 ELSE 1 END LIMIT 1",
                        p,
                        (rs, rowNum) -> rs.getLong(1));
        long limit = limits.isEmpty() ? 100_000L : limits.get(0);
        Long used =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_usage_events WHERE billing_tenant_id=:tenant "
                                + "AND created_at>=UTC_DATE()",
                        p,
                        Long.class);
        if (used != null && used >= limit) {
            throw new PaymentGatewayException("Daily BaaS usage-event quota exceeded");
        }
    }

    private void requireContext(BillingBaasContext context) {
        if (context == null || context.billingTenantId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS tenant context is required");
        }
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new PaymentGatewayException(field + " must be greater than zero");
        }
        return value;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
