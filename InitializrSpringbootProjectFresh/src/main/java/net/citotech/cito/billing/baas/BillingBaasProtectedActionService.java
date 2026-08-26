package net.citotech.cito.billing.baas;

import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingBaasProtectedActionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingBaasProtectedActionService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> request(
            BillingBaasContext context,
            String actionType,
            String resourceType,
            String resourceReference) {
        String action = required(actionType, "actionType").toUpperCase();
        String resource = required(resourceType, "resourceType").toUpperCase();
        String reference = required(resourceReference, "resourceReference");
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("action", action)
                        .addValue("resource", resource)
                        .addValue("reference", reference)
                        .addValue("actor", actor(context));
        jdbcTemplate.update(
                "INSERT INTO billing_protected_action_requests "
                        + "(billing_tenant_id,action_type,resource_type,resource_reference,requested_by,status) "
                        + "VALUES (:tenant,:action,:resource,:reference,:actor,'PENDING')",
                p);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        if (id == null) {
            throw new PaymentGatewayException("Unable to create protected-action request");
        }
        return find(context.billingTenantId(), id);
    }

    @Transactional
    public Map<String, Object> approve(
            BillingBaasContext context, long requestId, String decisionReason) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("id", requestId)
                        .addValue("actor", actor(context))
                        .addValue("reason", blankToNull(decisionReason));
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_protected_action_requests SET status='APPROVED',approved_by=:actor,"
                                + "approved_at=CURRENT_TIMESTAMP,decision_reason=:reason "
                                + "WHERE id=:id AND billing_tenant_id=:tenant AND status='PENDING' "
                                + "AND requested_by<>:actor",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Protected action requires PENDING status and a different service account approver");
        }
        return find(context.billingTenantId(), requestId);
    }

    @Transactional
    public Map<String, Object> reject(
            BillingBaasContext context, long requestId, String decisionReason) {
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("id", requestId)
                        .addValue("actor", actor(context))
                        .addValue("reason", required(decisionReason, "decisionReason"));
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_protected_action_requests SET status='REJECTED',approved_by=:actor,"
                                + "approved_at=CURRENT_TIMESTAMP,decision_reason=:reason "
                                + "WHERE id=:id AND billing_tenant_id=:tenant AND status='PENDING' "
                                + "AND requested_by<>:actor",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Protected action requires PENDING status and a different service account reviewer");
        }
        return find(context.billingTenantId(), requestId);
    }

    public Map<String, Object> find(long tenantId, long requestId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,action_type AS actionType,resource_type AS resourceType,"
                                + "resource_reference AS resourceReference,requested_by AS requestedBy,"
                                + "requested_at AS requestedAt,status,approved_by AS approvedBy,"
                                + "approved_at AS approvedAt,decision_reason AS decisionReason "
                                + "FROM billing_protected_action_requests WHERE id=:id AND billing_tenant_id=:tenant",
                        new MapSqlParameterSource().addValue("id", requestId).addValue("tenant", tenantId));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Protected-action request was not found for this tenant");
        }
        return rows.get(0);
    }

    private String actor(BillingBaasContext context) {
        if (context == null || context.serviceAccountId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS service account is required");
        }
        return "SERVICE_ACCOUNT:" + context.serviceAccountId();
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
