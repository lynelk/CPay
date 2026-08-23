package net.citotech.cito.developer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.CitoEntitlementService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Maps developer API scopes to the Cito product entitlement that owns the capability. */
@Service
public class DeveloperScopeEntitlementService {
    private static final Map<String, String> SCOPE_SERVICES = scopeServices();

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoEntitlementService entitlementService;

    public DeveloperScopeEntitlementService(
            NamedParameterJdbcTemplate jdbcTemplate, CitoEntitlementService entitlementService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementService = entitlementService;
    }

    public void requireScopes(long merchantId, List<String> scopes, String environment) {
        if (scopes == null || scopes.isEmpty()) {
            throw new PaymentGatewayException("At least one scope is required");
        }
        Set<String> services = new LinkedHashSet<>();
        for (String raw : scopes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String scope = raw.trim().toUpperCase(Locale.ROOT);
            String service = SCOPE_SERVICES.get(scope);
            if (service == null) {
                throw new PaymentGatewayException("Unsupported developer scope: " + scope);
            }
            services.add(service);
        }
        for (String service : services) {
            entitlementService.requireEntitlement(merchantId, service, environment);
        }
    }

    public Map<String, Object> productionReadiness(long merchantId, String projectReference) {
        long projectId = projectId(merchantId, projectReference);
        List<String> scopePayloads = jdbcTemplate.query(
                "SELECT CAST(scopes_json AS CHAR) FROM developer_service_accounts "
                        + "WHERE project_id=:project_id AND status='ACTIVE'",
                new MapSqlParameterSource("project_id", projectId),
                (rs, rowNum) -> rs.getString(1));
        Set<String> requiredServices = new LinkedHashSet<>();
        Set<String> scopes = new LinkedHashSet<>();
        for (String payload : scopePayloads) {
            String upper = payload == null ? "" : payload.toUpperCase(Locale.ROOT);
            for (Map.Entry<String, String> entry : SCOPE_SERVICES.entrySet()) {
                if (upper.contains("\"" + entry.getKey() + "\"")) {
                    scopes.add(entry.getKey());
                    requiredServices.add(entry.getValue());
                }
            }
        }
        List<String> missing = new ArrayList<>();
        for (String service : requiredServices) {
            if (!entitlementService.hasEntitlement(merchantId, service, "PRODUCTION")) {
                missing.add(service);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("projectReference", projectReference);
        result.put("scopes", scopes);
        result.put("requiredServices", requiredServices);
        result.put("missingProductionEntitlements", missing);
        result.put("productionReady", !scopes.isEmpty() && missing.isEmpty());
        return result;
    }

    public void requireProjectProductionEntitlements(long merchantId, String projectReference) {
        Map<String, Object> readiness = productionReadiness(merchantId, projectReference);
        @SuppressWarnings("unchecked")
        List<String> missing = (List<String>) readiness.get("missingProductionEntitlements");
        if (missing != null && !missing.isEmpty()) {
            throw new PaymentGatewayException(
                    "Production project activation requires service entitlements: "
                            + String.join(", ", missing));
        }
    }

    private long projectId(long merchantId, String reference) {
        if (reference == null || reference.isBlank()) {
            throw new PaymentGatewayException("projectReference is required");
        }
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM developer_projects WHERE merchant_id=:merchant_id "
                        + "AND project_reference=:reference AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference.trim()),
                (rs, rowNum) -> rs.getLong(1));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Developer project was not found or is inactive");
        }
        return rows.get(0);
    }

    private static Map<String, String> scopeServices() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("PAYMENTS_READ", "CPAY");
        map.put("PAYMENTS_WRITE", "CPAY");
        map.put("WEBHOOKS_READ", "CPAY");
        map.put("WEBHOOKS_WRITE", "CPAY");
        map.put("ROUTING_READ", "INTELLIGENT_ROUTING");
        map.put("ROUTING_WRITE", "INTELLIGENT_ROUTING");
        map.put("REFUNDS_READ", "REFUND_OPERATIONS");
        map.put("REFUNDS_WRITE", "REFUND_OPERATIONS");
        map.put("MARKETPLACE_READ", "MARKETPLACE_PAYMENTS");
        map.put("MARKETPLACE_WRITE", "MARKETPLACE_PAYMENTS");
        map.put("RECURRING_READ", "RECURRING_PAYMENTS");
        map.put("RECURRING_WRITE", "RECURRING_PAYMENTS");
        map.put("ANALYTICS_READ", "MERCHANT_ANALYTICS");
        map.put("VIRTUAL_ACCOUNTS_READ", "VIRTUAL_ACCOUNTS");
        map.put("VIRTUAL_ACCOUNTS_WRITE", "VIRTUAL_ACCOUNTS");
        map.put("EMBEDDED_READ", "EMBEDDED_CITO");
        map.put("EMBEDDED_WRITE", "EMBEDDED_CITO");
        map.put("INTEGRATIONS_READ", "INTEGRATIONS_MARKETPLACE");
        map.put("INTEGRATIONS_WRITE", "INTEGRATIONS_MARKETPLACE");
        map.put("IDENTITY_READ", "IDENTITY_VALIDATION");
        map.put("IDENTITY_WRITE", "IDENTITY_VALIDATION");
        map.put("COMMUNICATIONS_READ", "COMMUNICATIONS");
        map.put("COMMUNICATIONS_WRITE", "COMMUNICATIONS");
        return Map.copyOf(map);
    }
}
