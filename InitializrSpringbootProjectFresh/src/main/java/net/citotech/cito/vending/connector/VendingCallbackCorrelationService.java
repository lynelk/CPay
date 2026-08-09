package net.citotech.cito.vending.connector;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Correlates OEM callbacks to CPay rentals when the manufacturer echoes either CPay's command
 * reference or the OEM provider reference instead of the rental reference itself.
 *
 * <p>The mapping fields are tenant/connector configuration. This matters for real cabinets because
 * insisting that every OEM callback contains a CPay-specific rental field would merely replace one
 * guessed wire contract with another, which is not exactly an improvement.
 */
@Service
public class VendingCallbackCorrelationService {
    private final NamedParameterJdbcTemplate jdbc;

    public VendingCallbackCorrelationService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Mapping mapping(long merchantId, String connectorCode) {
        String sql =
                "SELECT callback_command_reference_field, callback_provider_reference_field "
                        + "FROM vending_connector_configs WHERE merchant_id=:tenant_merchant_id "
                        + "AND connector_code=:connector_code LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", normalize(connectorCode));
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) return new Mapping("", "");
        Map<String, Object> row = rows.get(0);
        return new Mapping(
                text(row.get("callback_command_reference_field")),
                text(row.get("callback_provider_reference_field")));
    }

    public Map<String, Object> save(
            long merchantId,
            String connectorCode,
            String commandReferenceField,
            String providerReferenceField) {
        String sql =
                "UPDATE vending_connector_configs SET callback_command_reference_field=:command_field, "
                        + "callback_provider_reference_field=:provider_field "
                        + "WHERE merchant_id=:tenant_merchant_id AND connector_code=:connector_code";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", normalize(connectorCode));
        p.addValue("command_field", blankToNull(commandReferenceField));
        p.addValue("provider_field", blankToNull(providerReferenceField));
        if (jdbc.update(sql, p) == 0) {
            throw new PaymentGatewayException("Vending connector configuration was not found");
        }
        Mapping mapping = mapping(merchantId, connectorCode);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("connectorCode", normalize(connectorCode));
        response.put("callbackCommandReferenceField", mapping.commandReferenceField());
        response.put("callbackProviderReferenceField", mapping.providerReferenceField());
        return response;
    }

    public String resolveRentalReference(
            long merchantId,
            String connectorCode,
            String commandReference,
            String providerReference) {
        String command = text(commandReference);
        String provider = text(providerReference);
        if (command.isBlank() && provider.isBlank()) return "";

        String sql =
                "SELECT r.rental_reference FROM vending_commands c "
                        + "JOIN vending_rentals r ON r.id=c.rental_id AND r.merchant_id=:tenant_merchant_id "
                        + "WHERE c.merchant_id=:tenant_merchant_id AND c.connector_code=:connector_code "
                        + "AND c.rental_id IS NOT NULL AND ((:command_reference<>'' AND c.command_reference=:command_reference) "
                        + "OR (:provider_reference<>'' AND c.provider_reference=:provider_reference)) "
                        + "ORDER BY c.id DESC LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", normalize(connectorCode));
        p.addValue("command_reference", command);
        p.addValue("provider_reference", provider);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        return rows.isEmpty() ? "" : text(rows.get(0).get("rental_reference"));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record Mapping(String commandReferenceField, String providerReferenceField) {}
}
