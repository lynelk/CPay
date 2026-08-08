package net.citotech.cito.admin;

import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/**
 * Tenant-aware repository guardrail (ADR 0002: CPay is the system of record for every shared
 * entity; tenant isolation is a first-class property).
 *
 * <p>Every merchant-scoped repository access must pass through {@link #scope} so the tenant key is
 * always bound, and {@link #assertTenantBound} rejects a merchant-scoped statement that references
 * rows without binding the tenant parameter. The cross-tenant isolation tests prove a merchant can
 * never read another merchant's rows through the registry.
 */
public final class TenantScopeGuard {

    public static final String TENANT_PARAM = "tenant_merchant_id";

    private TenantScopeGuard() {}

    /**
     * Returns a copy of {@code parameters} with the tenant key bound. The caller's map is never
     * mutated, so one shared parameter template can be reused across tenants safely.
     */
    public static MapSqlParameterSource scope(MapSqlParameterSource parameters, long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException(
                    "A valid merchant id is required for tenant-scoped access");
        }
        MapSqlParameterSource scoped = new MapSqlParameterSource();
        scoped.addValues(parameters == null ? Map.of() : parameters.getValues());
        scoped.addValue(TENANT_PARAM, merchantId);
        return scoped;
    }

    /**
     * Guards a merchant-scoped statement: the SQL must reference the tenant parameter. Catches
     * copy-paste errors that would otherwise silently widen a query to every merchant.
     */
    public static void assertTenantBound(String sql) {
        if (sql == null || !sql.contains(":" + TENANT_PARAM)) {
            throw new IllegalStateException("Merchant-scoped statement must bind :" + TENANT_PARAM);
        }
    }
}
