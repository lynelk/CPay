package net.citotech.cito.entitlements;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Central fail-closed entitlement boundary for Cito product modules. */
@Service
public class CitoServiceEntitlementService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CitoServiceEntitlementService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public EntitlementDecision requireMerchantAccess(
            long merchantId, String serviceCode, String environment, String actor) {
        EntitlementDecision decision = evaluate(merchantId, serviceCode, environment, Instant.now());
        audit(decision, merchantId, actor);
        if (!decision.allowed()) {
            throw new PaymentGatewayException(
                    "Cito service entitlement denied: "
                            + decision.serviceCode()
                            + " / "
                            + decision.environment()
                            + " ("
                            + decision.reason()
                            + ")");
        }
        return decision;
    }

    public EntitlementDecision evaluate(
            long merchantId, String serviceCode, String environment, Instant asOf) {
        if (merchantId <= 0 || asOf == null) {
            throw new PaymentGatewayException("Merchant and evaluation time are required");
        }
        String service = required(serviceCode, "serviceCode").toUpperCase(Locale.ROOT);
        String env = required(environment, "environment").toUpperCase(Locale.ROOT);
        if (!"SANDBOX".equals(env) && !"PRODUCTION".equals(env)) {
            throw new PaymentGatewayException("Environment must be SANDBOX or PRODUCTION");
        }

        List<Map<String, Object>> catalog =
                jdbcTemplate.queryForList(
                        "SELECT service_code,lifecycle_status,default_sandbox_access,default_production_access "
                                + "FROM cito_service_catalog WHERE service_code=:service LIMIT 1",
                        new MapSqlParameterSource("service", service));
        if (catalog.isEmpty()) {
            return new EntitlementDecision(false, service, env, null, "UNKNOWN_SERVICE");
        }
        Map<String, Object> serviceRow = catalog.get(0);
        if (!"ACTIVE".equals(String.valueOf(serviceRow.get("lifecycle_status")))) {
            return new EntitlementDecision(false, service, env, null, "SERVICE_NOT_ACTIVE");
        }

        List<Long> organizations =
                jdbcTemplate.query(
                        "SELECT id FROM cito_organizations WHERE merchant_id=:merchant "
                                + "AND organization_status='ACTIVE' ORDER BY id LIMIT 2",
                        new MapSqlParameterSource("merchant", merchantId),
                        (rs, rowNum) -> rs.getLong(1));
        if (organizations.size() > 1) {
            throw new PaymentGatewayException("Merchant resolves to multiple active Cito organizations");
        }
        Long organizationId = organizations.isEmpty() ? null : organizations.get(0);

        if (organizationId != null) {
            List<Map<String, Object>> rows =
                    jdbcTemplate.queryForList(
                            "SELECT entitlement_status,valid_from,valid_to FROM cito_service_entitlements "
                                    + "WHERE organization_id=:organization AND service_code=:service "
                                    + "AND environment=:environment LIMIT 1",
                            new MapSqlParameterSource()
                                    .addValue("organization", organizationId)
                                    .addValue("service", service)
                                    .addValue("environment", env));
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                String status = String.valueOf(row.get("entitlement_status"));
                if (!"ACTIVE".equals(status)) {
                    return new EntitlementDecision(false, service, env, organizationId, "ENTITLEMENT_" + status);
                }
                Instant validFrom = ((Timestamp) row.get("valid_from")).toInstant();
                Object validToValue = row.get("valid_to");
                Instant validTo = validToValue == null ? null : ((Timestamp) validToValue).toInstant();
                if (validFrom.isAfter(asOf) || (validTo != null && !validTo.isAfter(asOf))) {
                    return new EntitlementDecision(false, service, env, organizationId, "OUTSIDE_VALIDITY_WINDOW");
                }
                return new EntitlementDecision(true, service, env, organizationId, "ACTIVE_ENTITLEMENT");
            }
        }

        boolean defaultAccess =
                "SANDBOX".equals(env)
                        ? "YES".equals(String.valueOf(serviceRow.get("default_sandbox_access")))
                        : "YES".equals(String.valueOf(serviceRow.get("default_production_access")));
        return new EntitlementDecision(
                defaultAccess,
                service,
                env,
                organizationId,
                defaultAccess ? "CATALOG_DEFAULT" : "NO_ACTIVE_ENTITLEMENT");
    }

    private void audit(EntitlementDecision decision, long merchantId, String actor) {
        if (decision.organizationId() == null) {
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO cito_service_access_audit "
                        + "(organization_id,service_code,environment,decision,reason,actor) "
                        + "VALUES (:organization,:service,:environment,:decision,:reason,:actor)",
                new MapSqlParameterSource()
                        .addValue("organization", decision.organizationId())
                        .addValue("service", decision.serviceCode())
                        .addValue("environment", decision.environment())
                        .addValue("decision", decision.allowed() ? "ALLOW" : "DENY")
                        .addValue("reason", decision.reason())
                        .addValue("actor", actor == null || actor.isBlank() ? "system" : actor));
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    public record EntitlementDecision(
            boolean allowed,
            String serviceCode,
            String environment,
            Long organizationId,
            String reason) {}
}
