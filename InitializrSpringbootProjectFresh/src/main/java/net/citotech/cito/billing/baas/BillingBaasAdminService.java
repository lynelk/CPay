package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.Model.TransactionStatus;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.billing.integration.cpay.BillingLedgerAccountTemplateService;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.CitoEntitlementService;
import org.json.JSONArray;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillingBaasAdminService {
    private static final Set<String> BILLING_SCOPES =
            Set.of("BILLING_READ", "BILLING_WRITE", "BILLING_CHARGE");
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoEntitlementService entitlementService;
    private final BillingLedgerAccountTemplateService ledgerService;
    private final AdminAuditService auditService;

    public BillingBaasAdminService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CitoEntitlementService entitlementService,
            BillingLedgerAccountTemplateService ledgerService,
            AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementService = entitlementService;
        this.ledgerService = ledgerService;
        this.auditService = auditService;
    }

    @Transactional
    public Map<String, Object> reviewTenant(
            long billingTenantId,
            String legalStatus,
            String commercialStatus,
            String taxStatus,
            String fundsFlowStatus,
            String actor) {
        requireTenant(billingTenantId);
        String legal = reviewStatus(legalStatus, "legalStatus");
        String commercial = reviewStatus(commercialStatus, "commercialStatus");
        String tax = reviewStatus(taxStatus, "taxStatus");
        String funds = reviewStatus(fundsFlowStatus, "fundsFlowStatus");
        boolean ready =
                "APPROVED".equals(legal)
                        && "APPROVED".equals(commercial)
                        && "APPROVED".equals(tax)
                        && "APPROVED".equals(funds);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("legal", legal)
                        .addValue("commercial", commercial)
                        .addValue("tax", tax)
                        .addValue("funds", funds)
                        .addValue("activation", ready ? "READY" : "DRAFT")
                        .addValue("actor", required(actor, "actor"));
        jdbcTemplate.update(
                "INSERT INTO billing_baas_tenant_profiles "
                        + "(billing_tenant_id,legal_model_status,commercial_model_status,tax_model_status,"
                        + "funds_flow_status,activation_status,approved_by,approved_at) "
                        + "VALUES (:tenant,:legal,:commercial,:tax,:funds,:activation,"
                        + "CASE WHEN :activation='READY' THEN :actor ELSE NULL END,"
                        + "CASE WHEN :activation='READY' THEN CURRENT_TIMESTAMP ELSE NULL END) "
                        + "ON DUPLICATE KEY UPDATE legal_model_status=VALUES(legal_model_status),"
                        + "commercial_model_status=VALUES(commercial_model_status),"
                        + "tax_model_status=VALUES(tax_model_status),funds_flow_status=VALUES(funds_flow_status),"
                        + "activation_status=CASE WHEN activation_status='ACTIVE' AND :activation='READY' "
                        + "THEN 'ACTIVE' ELSE :activation END,approved_by=VALUES(approved_by),"
                        + "approved_at=VALUES(approved_at),updated_at=CURRENT_TIMESTAMP",
                p);
        auditService.record(
                "BILLING_BAAS",
                "BAAS_TENANT_REVIEW",
                String.valueOf(billingTenantId),
                "legal=" + legal + ",commercial=" + commercial + ",tax=" + tax + ",funds=" + funds);
        return tenantProfile(billingTenantId);
    }

    @Transactional
    public Map<String, Object> activateTenant(long billingTenantId, String actor) {
        requireTenant(billingTenantId);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("actor", required(actor, "actor"));
        int updated =
                jdbcTemplate.update(
                        "UPDATE billing_baas_tenant_profiles SET activation_status='ACTIVE',"
                                + "activated_by=:actor,activated_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP "
                                + "WHERE billing_tenant_id=:tenant AND activation_status IN ('READY','ACTIVE') "
                                + "AND legal_model_status='APPROVED' AND commercial_model_status='APPROVED' "
                                + "AND tax_model_status='APPROVED' AND funds_flow_status='APPROVED' "
                                + "AND approved_by IS NOT NULL AND approved_by<>:actor",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "BaaS tenant activation requires all reviews approved and a different activator from the reviewer");
        }
        auditService.record(
                "BILLING_BAAS",
                "BAAS_TENANT_ACTIVATE",
                String.valueOf(billingTenantId),
                "actor=" + actor);
        return tenantProfile(billingTenantId);
    }

    @Transactional
    public Map<String, Object> provisionCredential(
            long billingTenantId,
            String projectReference,
            String environment,
            String displayName,
            List<String> scopes,
            Integer requestsPerMinute,
            Instant expiresAt,
            String actor) {
        String env = environment(environment);
        Project project = resolveProject(billingTenantId, projectReference);
        entitlementService.ensureMerchantOrganization(project.merchantId());
        entitlementService.requireEntitlement(project.merchantId(), "BILLING", env);
        requireProjectEnvironment(project.id(), env);
        if ("PRODUCTION".equals(env)) {
            requireActiveProfile(billingTenantId);
        }
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new PaymentGatewayException("BaaS credential expiresAt must be in the future");
        }
        List<String> normalizedScopes = normalizeScopes(scopes);
        String serviceAccountReference = reference("BAASSA");
        String credentialReference = reference("BAASKEY");
        String secret = "cito_billing_" + Common.randomUrlSafeToken(32);
        String keyPrefix = secret.substring(0, Math.min(24, secret.length()));
        String safeActor = required(actor, "actor");

        jdbcTemplate.update(
                "INSERT INTO billing_tenant_developer_projects "
                        + "(billing_tenant_id,developer_project_id,environment,status) "
                        + "VALUES (:tenant,:project,:environment,'ACTIVE') "
                        + "ON DUPLICATE KEY UPDATE status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("project", project.id())
                        .addValue("environment", env));
        jdbcTemplate.update(
                "INSERT INTO developer_service_accounts "
                        + "(project_id,service_account_reference,display_name,scopes_json,status,created_by) "
                        + "VALUES (:project,:reference,:display_name,:scopes,'ACTIVE',:actor)",
                new MapSqlParameterSource()
                        .addValue("project", project.id())
                        .addValue("reference", serviceAccountReference)
                        .addValue("display_name", required(displayName, "displayName"))
                        .addValue("scopes", new JSONArray(normalizedScopes).toString())
                        .addValue("actor", safeActor));
        Long serviceAccountId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM developer_service_accounts WHERE service_account_reference=:reference",
                        new MapSqlParameterSource("reference", serviceAccountReference),
                        Long.class);
        if (serviceAccountId == null) {
            throw new PaymentGatewayException("Unable to create BaaS service account");
        }
        jdbcTemplate.update(
                "INSERT INTO developer_credentials "
                        + "(service_account_id,credential_reference,key_prefix,secret_hash,status,expires_at) "
                        + "VALUES (:service_account,:reference,:prefix,:hash,'ACTIVE',:expires_at)",
                new MapSqlParameterSource()
                        .addValue("service_account", serviceAccountId)
                        .addValue("reference", credentialReference)
                        .addValue("prefix", keyPrefix)
                        .addValue("hash", sha256(secret))
                        .addValue(
                                "expires_at",
                                expiresAt == null ? null : Timestamp.from(expiresAt)));
        int rpm = requestsPerMinute == null ? 300 : requestsPerMinute;
        if (rpm <= 0 || rpm > 100000) {
            throw new PaymentGatewayException("requestsPerMinute must be between 1 and 100000");
        }
        jdbcTemplate.update(
                "INSERT INTO billing_api_quota_policies "
                        + "(billing_tenant_id,developer_project_id,environment,requests_per_minute,status) "
                        + "VALUES (:tenant,:project,:environment,:rpm,'ACTIVE') "
                        + "ON DUPLICATE KEY UPDATE requests_per_minute=:rpm,status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("project", project.id())
                        .addValue("environment", env)
                        .addValue("rpm", rpm));
        auditService.record(
                "BILLING_BAAS",
                "BAAS_CREDENTIAL_PROVISION",
                credentialReference,
                "tenant="
                        + billingTenantId
                        + ",project="
                        + projectReference
                        + ",environment="
                        + env);
        return Map.of(
                "billingTenantId",
                billingTenantId,
                "projectReference",
                projectReference,
                "environment",
                env,
                "serviceAccountReference",
                serviceAccountReference,
                "credentialReference",
                credentialReference,
                "keyPrefix",
                keyPrefix,
                "secret",
                secret,
                "displayOnce",
                true,
                "scopes",
                normalizedScopes,
                "requestsPerMinute",
                rpm);
    }

    @Transactional
    public Map<String, Object> setCreditLimit(
            long billingTenantId, String accountReference, BigDecimal creditLimit, String actor) {
        BigDecimal limit = nonNegative(creditLimit, "creditLimit");
        BillingAccount account = billingAccount(billingTenantId, accountReference);
        BigDecimal creditUsed =
                chargingCreditUsed(billingTenantId, account.id(), account.currency());
        if (limit.compareTo(creditUsed) < 0) {
            throw new PaymentGatewayException(
                    "Credit limit cannot be lower than currently used credit");
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("account", account.id())
                        .addValue("currency", account.currency())
                        .addValue("customer", account.customerId())
                        .addValue("limit", limit);
        jdbcTemplate.update(
                "UPDATE billing_accounts SET credit_limit=:limit WHERE id=:account AND billing_tenant_id=:tenant",
                p);
        jdbcTemplate.update(
                "INSERT INTO billing_charging_accounts "
                        + "(billing_tenant_id,billing_customer_id,billing_account_id,currency,credit_limit) "
                        + "VALUES (:tenant,:customer,:account,:currency,:limit) "
                        + "ON DUPLICATE KEY UPDATE credit_limit=:limit,lock_version=lock_version+1",
                p);
        auditService.record(
                "BILLING_BAAS",
                "BAAS_CREDIT_LIMIT",
                accountReference,
                "tenant=" + billingTenantId + ",limit=" + limit + ",actor=" + actor);
        return chargingAccount(billingTenantId, account.id(), account.currency());
    }

    @Transactional
    public Map<String, Object> topUp(
            long billingTenantId,
            String accountReference,
            BigDecimal amount,
            String verifiedPaymentReference,
            String actor) {
        BigDecimal safeAmount = positive(amount, "amount");
        String paymentReference = required(verifiedPaymentReference, "verifiedPaymentReference");
        BillingAccount account = billingAccount(billingTenantId, accountReference);
        verifySettledPayIn(billingTenantId, paymentReference, safeAmount);
        ensureChargingAccount(billingTenantId, account);
        String adjustmentKey = "topup:" + billingTenantId + ":" + paymentReference;
        Integer existing =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_charging_adjustments "
                                + "WHERE billing_tenant_id=:tenant AND idempotency_key=:key",
                        new MapSqlParameterSource()
                                .addValue("tenant", billingTenantId)
                                .addValue("key", adjustmentKey),
                        Integer.class);
        if (existing != null && existing > 0) {
            return chargingAccount(billingTenantId, account.id(), account.currency());
        }
        long ledgerTransactionId =
                ledgerService.postPrepaidTopUp(
                        billingTenantId,
                        account.currency(),
                        safeAmount,
                        billingTenantId + ":" + paymentReference,
                        "Verified BaaS prepaid top-up " + paymentReference);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("account", account.id())
                        .addValue("amount", safeAmount)
                        .addValue("ledger", ledgerTransactionId)
                        .addValue("key", adjustmentKey)
                        .addValue("actor", required(actor, "actor"));
        jdbcTemplate.update(
                "UPDATE billing_charging_accounts SET prepaid_balance=prepaid_balance+:amount,"
                        + "lock_version=lock_version+1 WHERE billing_tenant_id=:tenant AND billing_account_id=:account",
                p);
        Long chargingAccountId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM billing_charging_accounts WHERE billing_tenant_id=:tenant "
                                + "AND billing_account_id=:account",
                        p,
                        Long.class);
        jdbcTemplate.update(
                "INSERT INTO billing_charging_adjustments "
                        + "(billing_tenant_id,charging_account_id,adjustment_type,prepaid_delta,"
                        + "ledger_transaction_id,idempotency_key,created_by) "
                        + "VALUES (:tenant,:charging_account,'TOP_UP',:amount,:ledger,:key,:actor)",
                new MapSqlParameterSource()
                        .addValue("tenant", billingTenantId)
                        .addValue("charging_account", chargingAccountId)
                        .addValue("amount", safeAmount)
                        .addValue("ledger", ledgerTransactionId)
                        .addValue("key", adjustmentKey)
                        .addValue("actor", actor));
        auditService.record(
                "BILLING_BAAS",
                "BAAS_PREPAID_TOPUP",
                accountReference,
                "tenant=" + billingTenantId + ",paymentReference=" + paymentReference);
        return chargingAccount(billingTenantId, account.id(), account.currency());
    }

    public Map<String, Object> tenantProfile(long billingTenantId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT billing_tenant_id AS billingTenantId,legal_model_status AS legalModelStatus,"
                                + "commercial_model_status AS commercialModelStatus,tax_model_status AS taxModelStatus,"
                                + "funds_flow_status AS fundsFlowStatus,activation_status AS activationStatus,"
                                + "approved_by AS approvedBy,approved_at AS approvedAt,suspended_by AS suspendedBy,"
                                + "suspended_at AS suspendedAt,suspension_reason AS suspensionReason,updated_at AS updatedAt "
                                + "FROM billing_baas_tenant_profiles WHERE billing_tenant_id=:tenant",
                        new MapSqlParameterSource("tenant", billingTenantId));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("BaaS tenant profile was not found");
        }
        return rows.get(0);
    }

    private Project resolveProject(long tenantId, String projectReference) {
        List<Project> rows =
                jdbcTemplate.query(
                        "SELECT p.id,p.merchant_id FROM developer_projects p "
                                + "JOIN billing_tenants bt ON bt.merchant_id=p.merchant_id "
                                + "WHERE bt.id=:tenant AND p.project_reference=:reference AND p.status='ACTIVE'",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue(
                                        "reference",
                                        required(projectReference, "projectReference")),
                        (rs, rowNum) -> new Project(rs.getLong("id"), rs.getLong("merchant_id")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "Developer project was not found for this billing tenant merchant");
        }
        return rows.get(0);
    }

    private void requireProjectEnvironment(long projectId, String environment) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM developer_project_environments "
                                + "WHERE project_id=:project AND environment=:environment AND status='ACTIVE' "
                                + "AND (:environment<>'PRODUCTION' OR production_eligible='YES')",
                        new MapSqlParameterSource()
                                .addValue("project", projectId)
                                .addValue("environment", environment),
                        Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException(
                    "Developer project environment is not active or production eligible");
        }
    }

    private void requireActiveProfile(long tenantId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_baas_tenant_profiles WHERE billing_tenant_id=:tenant "
                                + "AND legal_model_status='APPROVED' AND commercial_model_status='APPROVED' "
                                + "AND tax_model_status='APPROVED' AND funds_flow_status='APPROVED' "
                                + "AND activation_status='ACTIVE'",
                        new MapSqlParameterSource("tenant", tenantId),
                        Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException("BaaS tenant is not approved for production");
        }
    }

    private void requireTenant(long tenantId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM billing_tenants WHERE id=:tenant",
                        new MapSqlParameterSource("tenant", tenantId),
                        Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException("Billing tenant was not found");
        }
    }

    private BillingAccount billingAccount(long tenantId, String reference) {
        List<BillingAccount> rows =
                jdbcTemplate.query(
                        "SELECT id,billing_customer_id,currency,credit_limit FROM billing_accounts "
                                + "WHERE billing_tenant_id=:tenant AND external_reference=:reference AND account_status='ACTIVE'",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", required(reference, "accountReference")),
                        (rs, rowNum) ->
                                new BillingAccount(
                                        rs.getLong("id"),
                                        rs.getLong("billing_customer_id"),
                                        rs.getString("currency"),
                                        rs.getBigDecimal("credit_limit")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Billing account was not found for this tenant");
        }
        return rows.get(0);
    }

    private void verifySettledPayIn(long tenantId, String paymentReference, BigDecimal amount) {
        List<PaymentProof> proofs =
                jdbcTemplate.query(
                        "SELECT t.status,t.tx_type,t.original_amount FROM merchant_transactions_log t "
                                + "JOIN billing_tenants bt ON bt.merchant_id=t.merchant_id "
                                + "WHERE bt.id=:tenant AND (t.tx_unique_id=:reference OR t.tx_merchant_ref=:reference "
                                + "OR t.tx_gateway_ref=:reference) ORDER BY t.id DESC LIMIT 2",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("reference", paymentReference),
                        (rs, rowNum) ->
                                new PaymentProof(
                                        rs.getString("status"),
                                        rs.getString("tx_type"),
                                        rs.getBigDecimal("original_amount")));
        if (proofs.size() != 1) {
            throw new PaymentGatewayException(
                    "Verified payment reference must resolve to exactly one CPay transaction for this tenant");
        }
        PaymentProof proof = proofs.get(0);
        TransactionStatus status = TransactionStatus.fromString(proof.status());
        if (status != TransactionStatus.SUCCESSFUL) {
            throw new PaymentGatewayException("Prepaid funding requires a SUCCESSFUL CPay payment");
        }
        if (!Transaction.TX_TYPE_PAYIN.equals(proof.transactionType())) {
            throw new PaymentGatewayException("Prepaid funding requires a CPay PAYIN transaction");
        }
        if (proof.amount() == null || proof.amount().compareTo(amount) != 0) {
            throw new PaymentGatewayException(
                    "Prepaid top-up amount must exactly match the settled CPay payment amount");
        }
    }

    private void ensureChargingAccount(long tenantId, BillingAccount account) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO billing_charging_accounts "
                        + "(billing_tenant_id,billing_customer_id,billing_account_id,currency,credit_limit) "
                        + "VALUES (:tenant,:customer,:account,:currency,:limit)",
                new MapSqlParameterSource()
                        .addValue("tenant", tenantId)
                        .addValue("customer", account.customerId())
                        .addValue("account", account.id())
                        .addValue("currency", account.currency())
                        .addValue("limit", account.creditLimit()));
    }

    private BigDecimal chargingCreditUsed(long tenantId, long accountId, String currency) {
        List<BigDecimal> rows =
                jdbcTemplate.query(
                        "SELECT credit_used FROM billing_charging_accounts WHERE billing_tenant_id=:tenant "
                                + "AND billing_account_id=:account AND currency=:currency",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("account", accountId)
                                .addValue("currency", currency),
                        (rs, rowNum) -> rs.getBigDecimal(1));
        return rows.isEmpty() ? BigDecimal.ZERO : rows.get(0);
    }

    private Map<String, Object> chargingAccount(long tenantId, long accountId, String currency) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,billing_account_id AS billingAccountId,currency,prepaid_balance AS prepaidBalance,"
                                + "credit_limit AS creditLimit,credit_used AS creditUsed,reserved_amount AS reservedAmount,"
                                + "status,updated_at AS updatedAt FROM billing_charging_accounts "
                                + "WHERE billing_tenant_id=:tenant AND billing_account_id=:account AND currency=:currency",
                        new MapSqlParameterSource()
                                .addValue("tenant", tenantId)
                                .addValue("account", accountId)
                                .addValue("currency", currency));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Charging account was not found");
        }
        return rows.get(0);
    }

    private List<String> normalizeScopes(List<String> scopes) {
        List<String> source =
                scopes == null || scopes.isEmpty() ? List.copyOf(BILLING_SCOPES) : scopes;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : source) {
            String scope = required(raw, "scope").toUpperCase(Locale.ROOT);
            if (!BILLING_SCOPES.contains(scope)) {
                throw new PaymentGatewayException("Unsupported BaaS scope: " + scope);
            }
            normalized.add(scope);
        }
        return List.copyOf(normalized);
    }

    private String reviewStatus(String value, String field) {
        String status = required(value, field).toUpperCase(Locale.ROOT);
        if (!REVIEW_STATUSES.contains(status)) {
            throw new PaymentGatewayException(field + " must be PENDING, APPROVED or REJECTED");
        }
        return status;
    }

    private String environment(String value) {
        String env = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!"SANDBOX".equals(env) && !"PRODUCTION".equals(env)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return env;
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private BigDecimal positive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) {
            throw new PaymentGatewayException(field + " must be greater than zero");
        }
        return value;
    }

    private BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new PaymentGatewayException(field + " must be zero or greater");
        }
        return value;
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String sha256(String value) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private record Project(long id, long merchantId) {}

    private record PaymentProof(String status, String transactionType, BigDecimal amount) {}

    private record BillingAccount(
            long id, long customerId, String currency, BigDecimal creditLimit) {}
}
