package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingBaasCommercialService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BillingBaasCommercialService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> createCustomer(
            BillingBaasContext context,
            String externalReference,
            String displayName,
            String legalName,
            String email,
            String metadataJson) {
        requireContext(context);
        String reference = required(externalReference, "externalReference");
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("reference", reference)
                        .addValue("display_name", required(displayName, "displayName"))
                        .addValue("legal_name", blankToNull(legalName))
                        .addValue("email", blankToNull(email))
                        .addValue("metadata", jsonOrNull(metadataJson));
        jdbcTemplate.update(
                "INSERT INTO billing_customers "
                        + "(billing_tenant_id,external_reference,customer_type,display_name,legal_name,email,customer_status,metadata_json) "
                        + "VALUES (:tenant,:reference,'BAAS_END_CUSTOMER',:display_name,:legal_name,:email,'ACTIVE',:metadata) "
                        + "ON DUPLICATE KEY UPDATE display_name=VALUES(display_name),legal_name=VALUES(legal_name),"
                        + "email=VALUES(email),metadata_json=VALUES(metadata_json),updated_at=CURRENT_TIMESTAMP",
                p);
        return customer(context.billingTenantId(), reference);
    }

    @Transactional
    public Map<String, Object> createAccount(
            BillingBaasContext context,
            String customerReference,
            String accountReference,
            String currency) {
        requireContext(context);
        long customerId = customerId(context.billingTenantId(), customerReference);
        String reference = required(accountReference, "accountReference");
        String ccy = required(currency, "currency").toUpperCase();
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("customer", customerId)
                        .addValue("reference", reference)
                        .addValue("currency", ccy);
        jdbcTemplate.update(
                "INSERT INTO billing_accounts "
                        + "(billing_tenant_id,billing_customer_id,external_reference,currency,credit_limit,account_status) "
                        + "VALUES (:tenant,:customer,:reference,:currency,0,'ACTIVE') "
                        + "ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP",
                p);
        return account(context.billingTenantId(), reference);
    }

    @Transactional
    public Map<String, Object> createContract(
            BillingBaasContext context,
            String customerReference,
            String contractReference,
            String currency,
            Instant effectiveFrom,
            Instant effectiveTo,
            String termsJson) {
        requireContext(context);
        long customerId = customerId(context.billingTenantId(), customerReference);
        Instant start = effectiveFrom == null ? Instant.now() : effectiveFrom;
        if (effectiveTo != null && !effectiveTo.isAfter(start)) {
            throw new PaymentGatewayException("Contract effectiveTo must be after effectiveFrom");
        }
        String reference = required(contractReference, "contractReference");
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("customer", customerId)
                        .addValue("reference", reference)
                        .addValue("currency", required(currency, "currency").toUpperCase())
                        .addValue("effective_from", Timestamp.from(start))
                        .addValue(
                                "effective_to",
                                effectiveTo == null ? null : Timestamp.from(effectiveTo))
                        .addValue("terms", jsonOrNull(termsJson))
                        .addValue("actor", actor(context));
        jdbcTemplate.update(
                "INSERT INTO billing_contracts "
                        + "(billing_tenant_id,billing_customer_id,contract_reference,currency,status,effective_from,effective_to,terms_json,created_by) "
                        + "VALUES (:tenant,:customer,:reference,:currency,'DRAFT',:effective_from,:effective_to,:terms,:actor)",
                p);
        return contract(context.billingTenantId(), reference);
    }

    @Transactional
    public Map<String, Object> submitContract(
            BillingBaasContext context, String contractReference) {
        requireContext(context);
        MapSqlParameterSource p = contractParams(context, contractReference);
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_contracts SET status='SUBMITTED',submitted_by=:actor,"
                                + "submitted_at=CURRENT_TIMESTAMP WHERE billing_tenant_id=:tenant "
                                + "AND contract_reference=:reference AND status='DRAFT'",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException("Only a DRAFT contract can be submitted");
        }
        return contract(
                context.billingTenantId(), required(contractReference, "contractReference"));
    }

    @Transactional
    public Map<String, Object> approveContract(
            BillingBaasContext context, String contractReference) {
        requireContext(context);
        MapSqlParameterSource p = contractParams(context, contractReference);
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_contracts SET status='APPROVED',approved_by=:actor,"
                                + "approved_at=CURRENT_TIMESTAMP WHERE billing_tenant_id=:tenant "
                                + "AND contract_reference=:reference AND status='SUBMITTED' "
                                + "AND submitted_by IS NOT NULL AND submitted_by<>:actor",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Contract approval requires SUBMITTED status and a different service account");
        }
        return contract(
                context.billingTenantId(), required(contractReference, "contractReference"));
    }

    @Transactional
    public Map<String, Object> activateContract(
            BillingBaasContext context, String contractReference) {
        requireContext(context);
        MapSqlParameterSource p = contractParams(context, contractReference);
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_contracts SET status='ACTIVE' WHERE billing_tenant_id=:tenant "
                                + "AND contract_reference=:reference AND status='APPROVED' "
                                + "AND effective_from<=CURRENT_TIMESTAMP "
                                + "AND (effective_to IS NULL OR effective_to>CURRENT_TIMESTAMP)",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Contract must be APPROVED and currently effective before activation");
        }
        return contract(
                context.billingTenantId(), required(contractReference, "contractReference"));
    }

    @Transactional
    public Map<String, Object> createSubscription(
            BillingBaasContext context,
            String customerReference,
            String accountReference,
            String contractReference,
            String subscriptionReference,
            String serviceCode,
            String planCode,
            BigDecimal quantity,
            Instant startsAt,
            Instant endsAt) {
        requireContext(context);
        long customerId = customerId(context.billingTenantId(), customerReference);
        long accountId = accountId(context.billingTenantId(), customerId, accountReference);
        long contractId =
                activeContractId(context.billingTenantId(), customerId, contractReference);
        BigDecimal safeQuantity =
                positive(quantity == null ? BigDecimal.ONE : quantity, "quantity");
        Instant start = startsAt == null ? Instant.now() : startsAt;
        if (endsAt != null && !endsAt.isAfter(start)) {
            throw new PaymentGatewayException("Subscription endsAt must be after startsAt");
        }
        String reference = required(subscriptionReference, "subscriptionReference");
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("customer", customerId)
                        .addValue("account", accountId)
                        .addValue("contract", contractId)
                        .addValue("reference", reference)
                        .addValue("service", required(serviceCode, "serviceCode").toUpperCase())
                        .addValue("plan", required(planCode, "planCode"))
                        .addValue("quantity", safeQuantity)
                        .addValue("starts_at", Timestamp.from(start))
                        .addValue("ends_at", endsAt == null ? null : Timestamp.from(endsAt));
        jdbcTemplate.update(
                "INSERT INTO billing_subscriptions "
                        + "(billing_tenant_id,billing_customer_id,billing_account_id,billing_contract_id,"
                        + "subscription_reference,service_code,plan_code,quantity,status,starts_at,ends_at) "
                        + "VALUES (:tenant,:customer,:account,:contract,:reference,:service,:plan,:quantity,'PENDING',:starts_at,:ends_at)",
                p);
        return subscription(context.billingTenantId(), reference);
    }

    @Transactional
    public Map<String, Object> activateSubscription(
            BillingBaasContext context, String subscriptionReference) {
        requireContext(context);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue(
                                "reference",
                                required(subscriptionReference, "subscriptionReference"));
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_subscriptions s JOIN billing_contracts c ON c.id=s.billing_contract_id "
                                + "SET s.status='ACTIVE' WHERE s.billing_tenant_id=:tenant "
                                + "AND s.subscription_reference=:reference AND s.status IN ('PENDING','PAUSED') "
                                + "AND c.billing_tenant_id=:tenant AND c.status='ACTIVE' "
                                + "AND s.starts_at<=CURRENT_TIMESTAMP AND (s.ends_at IS NULL OR s.ends_at>CURRENT_TIMESTAMP)",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Subscription must be current and backed by an ACTIVE contract");
        }
        return subscription(
                context.billingTenantId(),
                required(subscriptionReference, "subscriptionReference"));
    }

    @Transactional
    public Map<String, Object> pauseSubscription(
            BillingBaasContext context, String subscriptionReference) {
        return changeSubscription(
                context, subscriptionReference, "ACTIVE", "PAUSED", "paused_at=CURRENT_TIMESTAMP");
    }

    @Transactional
    public Map<String, Object> cancelSubscription(
            BillingBaasContext context, String subscriptionReference) {
        requireContext(context);
        String reference = required(subscriptionReference, "subscriptionReference");
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_subscriptions SET status='CANCELLED',cancelled_at=CURRENT_TIMESTAMP "
                                + "WHERE billing_tenant_id=:tenant AND subscription_reference=:reference "
                                + "AND status IN ('PENDING','ACTIVE','PAUSED')",
                        new MapSqlParameterSource()
                                .addValue("tenant", context.billingTenantId())
                                .addValue("reference", reference));
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Subscription cannot be cancelled from its current state");
        }
        return subscription(context.billingTenantId(), reference);
    }

    @Transactional
    public Map<String, Object> grantEntitlement(
            BillingBaasContext context,
            String subscriptionReference,
            String entitlementCode,
            BigDecimal limitQuantity,
            Instant validFrom,
            Instant validTo) {
        requireContext(context);
        Map<String, Object> subscription =
                subscription(context.billingTenantId(), subscriptionReference);
        if (!"ACTIVE".equals(String.valueOf(subscription.get("status")))) {
            throw new PaymentGatewayException("Entitlements require an ACTIVE subscription");
        }
        long subscriptionId = ((Number) subscription.get("id")).longValue();
        long customerId = ((Number) subscription.get("billingCustomerId")).longValue();
        Instant start = validFrom == null ? Instant.now() : validFrom;
        if (validTo != null && !validTo.isAfter(start)) {
            throw new PaymentGatewayException("Entitlement validTo must be after validFrom");
        }
        if (limitQuantity != null && limitQuantity.signum() <= 0) {
            throw new PaymentGatewayException("Entitlement limitQuantity must be positive");
        }
        String code = required(entitlementCode, "entitlementCode");
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", context.billingTenantId())
                        .addValue("customer", customerId)
                        .addValue("subscription", subscriptionId)
                        .addValue("code", code)
                        .addValue("limit", limitQuantity)
                        .addValue("valid_from", Timestamp.from(start))
                        .addValue("valid_to", validTo == null ? null : Timestamp.from(validTo));
        jdbcTemplate.update(
                "INSERT INTO billing_entitlement_grants "
                        + "(billing_tenant_id,billing_customer_id,billing_subscription_id,entitlement_code,"
                        + "limit_quantity,valid_from,valid_to,status) "
                        + "VALUES (:tenant,:customer,:subscription,:code,:limit,:valid_from,:valid_to,'ACTIVE') "
                        + "ON DUPLICATE KEY UPDATE limit_quantity=VALUES(limit_quantity),valid_from=VALUES(valid_from),"
                        + "valid_to=VALUES(valid_to),status='ACTIVE'",
                p);
        return entitlement(context.billingTenantId(), subscriptionId, code);
    }

    public List<Map<String, Object>> customers(BillingBaasContext context) {
        requireContext(context);
        return jdbcTemplate.queryForList(
                "SELECT id,external_reference AS externalReference,display_name AS displayName,"
                        + "legal_name AS legalName,email,customer_status AS status,created_at AS createdAt "
                        + "FROM billing_customers WHERE billing_tenant_id=:tenant "
                        + "AND customer_type='BAAS_END_CUSTOMER' ORDER BY id DESC",
                new MapSqlParameterSource("tenant", context.billingTenantId()));
    }

    private Map<String, Object> changeSubscription(
            BillingBaasContext context,
            String subscriptionReference,
            String fromStatus,
            String toStatus,
            String timestampAssignment) {
        requireContext(context);
        String reference = required(subscriptionReference, "subscriptionReference");
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_subscriptions SET status=:to_status,"
                                + timestampAssignment
                                + " WHERE billing_tenant_id=:tenant AND subscription_reference=:reference "
                                + "AND status=:from_status",
                        new MapSqlParameterSource()
                                .addValue("tenant", context.billingTenantId())
                                .addValue("reference", reference)
                                .addValue("from_status", fromStatus)
                                .addValue("to_status", toStatus));
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Subscription cannot transition from " + fromStatus + " to " + toStatus);
        }
        return subscription(context.billingTenantId(), reference);
    }

    private MapSqlParameterSource contractParams(
            BillingBaasContext context, String contractReference) {
        return new MapSqlParameterSource()
                .addValue("tenant", context.billingTenantId())
                .addValue("reference", required(contractReference, "contractReference"))
                .addValue("actor", actor(context));
    }

    private Map<String, Object> customer(long tenantId, String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,external_reference AS externalReference,display_name AS displayName,"
                                + "legal_name AS legalName,email,customer_status AS status,created_at AS createdAt "
                                + "FROM billing_customers WHERE billing_tenant_id=:tenant AND external_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", reference));
        return one(rows, "Billing customer was not found");
    }

    private long customerId(long tenantId, String reference) {
        Map<String, Object> row = customer(tenantId, required(reference, "customerReference"));
        return ((Number) row.get("id")).longValue();
    }

    private Map<String, Object> account(long tenantId, String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_customer_id AS billingCustomerId,external_reference AS externalReference,"
                                + "currency,credit_limit AS creditLimit,account_status AS status,created_at AS createdAt "
                                + "FROM billing_accounts WHERE billing_tenant_id=:tenant AND external_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", reference));
        return one(rows, "Billing account was not found");
    }

    private long accountId(long tenantId, long customerId, String reference) {
        Map<String, Object> account = account(tenantId, required(reference, "accountReference"));
        if (((Number) account.get("billingCustomerId")).longValue() != customerId) {
            throw new PaymentGatewayException(
                    "Billing account does not belong to the requested customer");
        }
        return ((Number) account.get("id")).longValue();
    }

    private Map<String, Object> contract(long tenantId, String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_customer_id AS billingCustomerId,contract_reference AS contractReference,"
                                + "currency,status,effective_from AS effectiveFrom,effective_to AS effectiveTo,"
                                + "created_by AS createdBy,submitted_by AS submittedBy,approved_by AS approvedBy "
                                + "FROM billing_contracts WHERE billing_tenant_id=:tenant AND contract_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", reference));
        return one(rows, "Billing contract was not found");
    }

    private long activeContractId(long tenantId, long customerId, String reference) {
        Map<String, Object> contract = contract(tenantId, required(reference, "contractReference"));
        if (((Number) contract.get("billingCustomerId")).longValue() != customerId
                || !"ACTIVE".equals(String.valueOf(contract.get("status")))) {
            throw new PaymentGatewayException(
                    "Subscription contract must be ACTIVE and belong to the customer");
        }
        return ((Number) contract.get("id")).longValue();
    }

    private Map<String, Object> subscription(long tenantId, String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_customer_id AS billingCustomerId,billing_account_id AS billingAccountId,"
                                + "billing_contract_id AS billingContractId,subscription_reference AS subscriptionReference,"
                                + "service_code AS serviceCode,plan_code AS planCode,quantity,status,starts_at AS startsAt,"
                                + "ends_at AS endsAt,paused_at AS pausedAt,cancelled_at AS cancelledAt "
                                + "FROM billing_subscriptions WHERE billing_tenant_id=:tenant AND subscription_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue(
                                        "reference", required(reference, "subscriptionReference")));
        return one(rows, "Billing subscription was not found");
    }

    private Map<String, Object> entitlement(long tenantId, long subscriptionId, String code) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_subscription_id AS billingSubscriptionId,entitlement_code AS entitlementCode,"
                                + "limit_quantity AS limitQuantity,valid_from AS validFrom,valid_to AS validTo,status "
                                + "FROM billing_entitlement_grants WHERE billing_tenant_id=:tenant "
                                + "AND billing_subscription_id=:subscription AND entitlement_code=:code",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("subscription", subscriptionId)
                                .addValue("code", code));
        return one(rows, "Billing entitlement was not found");
    }

    private Map<String, Object> one(List<Map<String, Object>> rows, String message) {
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(message);
        }
        return rows.get(0);
    }

    private String actor(BillingBaasContext context) {
        return "SERVICE_ACCOUNT:" + context.serviceAccountId();
    }

    private String jsonOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            throw new PaymentGatewayException("JSON payload must be an object or array");
        }
        return trimmed;
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

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new PaymentGatewayException(field + " must be greater than zero");
        }
        return value;
    }

    private void requireContext(BillingBaasContext context) {
        if (context == null || context.billingTenantId() <= 0) {
            throw new PaymentGatewayException("Authenticated BaaS tenant context is required");
        }
    }
}
