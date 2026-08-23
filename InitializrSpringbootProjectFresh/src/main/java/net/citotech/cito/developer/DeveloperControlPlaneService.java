package net.citotech.cito.developer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.citotech.cito.Common;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.CitoEntitlementService;
import net.citotech.cito.webhook.MerchantWebhookService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeveloperControlPlaneService {
    private static final Set<String> ALLOWED_SCOPES =
            Set.of(
                    "PAYMENTS_READ",
                    "PAYMENTS_WRITE",
                    "REFUNDS_READ",
                    "REFUNDS_WRITE",
                    "MARKETPLACE_READ",
                    "MARKETPLACE_WRITE",
                    "ANALYTICS_READ",
                    "WEBHOOKS_READ",
                    "WEBHOOKS_WRITE",
                    "VIRTUAL_ACCOUNTS_READ",
                    "VIRTUAL_ACCOUNTS_WRITE");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoEntitlementService entitlementService;
    private final MerchantWebhookService webhookService;

    public DeveloperControlPlaneService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CitoEntitlementService entitlementService,
            MerchantWebhookService webhookService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementService = entitlementService;
        this.webhookService = webhookService;
    }

    @Transactional
    public Map<String, Object> createProject(
            long merchantId, String projectName, String description, String actor) {
        requireMerchant(merchantId);
        entitlementService.requireEntitlement(
                merchantId, "DEVELOPER_CONTROL_PLANE", "SANDBOX");
        String reference = reference("DEVPRJ");
        jdbcTemplate.update(
                "INSERT INTO developer_projects "
                        + "(merchant_id, project_reference, project_name, description, status, created_by) "
                        + "VALUES (:merchant_id, :reference, :project_name, :description, 'ACTIVE', :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference)
                        .addValue("project_name", required(projectName, "projectName"))
                        .addValue("description", blankToNull(description))
                        .addValue("created_by", blankToNull(actor)));
        long projectId = projectId(merchantId, reference);
        jdbcTemplate.update(
                "INSERT INTO developer_project_environments "
                        + "(project_id, environment, status, production_eligible, activated_by, activated_at) "
                        + "VALUES (:project_id, 'SANDBOX', 'ACTIVE', 'YES', 'SYSTEM', CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("project_id", projectId));
        return project(merchantId, reference);
    }

    public List<Map<String, Object>> projects(long merchantId) {
        requireMerchant(merchantId);
        return jdbcTemplate.queryForList(
                "SELECT p.project_reference AS projectReference, p.project_name AS projectName, p.description, p.status, "
                        + "p.created_by AS createdBy, p.created_at AS createdAt, p.updated_at AS updatedAt, "
                        + "GROUP_CONCAT(CONCAT(e.environment,':',e.status) ORDER BY e.environment SEPARATOR ',') AS environments "
                        + "FROM developer_projects p LEFT JOIN developer_project_environments e ON e.project_id=p.id "
                        + "WHERE p.merchant_id=:merchant_id GROUP BY p.id ORDER BY p.id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public Map<String, Object> activateEnvironment(
            long merchantId, String projectReference, String environment, String actor) {
        String env = environment(environment);
        long projectId = projectId(merchantId, projectReference);
        if ("PRODUCTION".equals(env)) {
            entitlementService.requireEntitlement(
                    merchantId, "DEVELOPER_CONTROL_PLANE", "PRODUCTION");
        }
        jdbcTemplate.update(
                "INSERT INTO developer_project_environments "
                        + "(project_id, environment, status, production_eligible, activated_by, activated_at) "
                        + "VALUES (:project_id, :environment, 'ACTIVE', 'YES', :actor, CURRENT_TIMESTAMP) "
                        + "ON DUPLICATE KEY UPDATE status='ACTIVE', production_eligible='YES', activated_by=:actor, activated_at=CURRENT_TIMESTAMP",
                new MapSqlParameterSource()
                        .addValue("project_id", projectId)
                        .addValue("environment", env)
                        .addValue("actor", required(actor, "actor")));
        return project(merchantId, projectReference);
    }

    @Transactional
    public Map<String, Object> createServiceAccount(
            long merchantId,
            String projectReference,
            String displayName,
            List<String> scopes,
            String actor) {
        long projectId = projectId(merchantId, projectReference);
        List<String> normalizedScopes = normalizeScopes(scopes);
        String reference = reference("DEVSA");
        jdbcTemplate.update(
                "INSERT INTO developer_service_accounts "
                        + "(project_id, service_account_reference, display_name, scopes_json, status, created_by) "
                        + "VALUES (:project_id, :reference, :display_name, :scopes_json, 'ACTIVE', :created_by)",
                new MapSqlParameterSource()
                        .addValue("project_id", projectId)
                        .addValue("reference", reference)
                        .addValue("display_name", required(displayName, "displayName"))
                        .addValue("scopes_json", jsonArray(normalizedScopes))
                        .addValue("created_by", blankToNull(actor)));
        return serviceAccount(merchantId, reference);
    }

    public List<Map<String, Object>> serviceAccounts(long merchantId, String projectReference) {
        long projectId = projectId(merchantId, projectReference);
        return jdbcTemplate.queryForList(
                "SELECT service_account_reference AS serviceAccountReference, display_name AS displayName, scopes_json AS scopes, "
                        + "status, created_by AS createdBy, created_at AS createdAt, revoked_at AS revokedAt "
                        + "FROM developer_service_accounts WHERE project_id=:project_id ORDER BY id DESC",
                new MapSqlParameterSource("project_id", projectId));
    }

    @Transactional
    public Map<String, Object> issueCredential(
            long merchantId,
            String serviceAccountReference,
            Instant expiresAt) {
        long serviceAccountId = serviceAccountId(merchantId, serviceAccountReference);
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new PaymentGatewayException("expiresAt must be in the future");
        }
        String credentialReference = reference("DEVKEY");
        String secret = "cito_" + Common.randomUrlSafeToken(32);
        String keyPrefix = secret.substring(0, Math.min(20, secret.length()));
        jdbcTemplate.update(
                "INSERT INTO developer_credentials "
                        + "(service_account_id, credential_reference, key_prefix, secret_hash, status, expires_at) "
                        + "VALUES (:service_account_id, :reference, :key_prefix, :secret_hash, 'ACTIVE', :expires_at)",
                new MapSqlParameterSource()
                        .addValue("service_account_id", serviceAccountId)
                        .addValue("reference", credentialReference)
                        .addValue("key_prefix", keyPrefix)
                        .addValue("secret_hash", sha256(secret))
                        .addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt)));
        return Map.of(
                "credentialReference",
                credentialReference,
                "keyPrefix",
                keyPrefix,
                "secret",
                secret,
                "displayOnce",
                true);
    }

    @Transactional
    public void revokeCredential(long merchantId, String credentialReference) {
        int updated = jdbcTemplate.update(
                "UPDATE developer_credentials c JOIN developer_service_accounts a ON a.id=c.service_account_id "
                        + "JOIN developer_projects p ON p.id=a.project_id SET c.status='REVOKED', c.revoked_at=CURRENT_TIMESTAMP "
                        + "WHERE p.merchant_id=:merchant_id AND c.credential_reference=:reference AND c.status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", required(credentialReference, "credentialReference")));
        if (updated == 0) {
            throw new PaymentGatewayException("Active credential was not found");
        }
    }

    public List<Map<String, Object>> credentials(long merchantId, String serviceAccountReference) {
        long serviceAccountId = serviceAccountId(merchantId, serviceAccountReference);
        return jdbcTemplate.queryForList(
                "SELECT credential_reference AS credentialReference, key_prefix AS keyPrefix, status, expires_at AS expiresAt, "
                        + "last_used_at AS lastUsedAt, created_at AS createdAt, revoked_at AS revokedAt "
                        + "FROM developer_credentials WHERE service_account_id=:service_account_id ORDER BY id DESC",
                new MapSqlParameterSource("service_account_id", serviceAccountId));
    }

    @Transactional
    public Map<String, Object> createTestEvent(
            long merchantId,
            String projectReference,
            String eventType,
            String payloadJson,
            String actor) {
        long projectId = projectId(merchantId, projectReference);
        String reference = reference("DEVTEST");
        String safePayload = payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson.trim();
        jdbcTemplate.update(
                "INSERT INTO developer_test_events "
                        + "(merchant_id, project_id, event_reference, event_type, payload_json, status, created_by) "
                        + "VALUES (:merchant_id, :project_id, :reference, :event_type, :payload_json, 'CREATED', :created_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("project_id", projectId)
                        .addValue("reference", reference)
                        .addValue("event_type", required(eventType, "eventType"))
                        .addValue("payload_json", safePayload)
                        .addValue("created_by", blankToNull(actor)));
        int queued = webhookService.testCallback(merchantId, eventType);
        jdbcTemplate.update(
                "UPDATE developer_test_events SET status=:status, dispatched_at=CASE WHEN :queued>0 THEN CURRENT_TIMESTAMP ELSE NULL END "
                        + "WHERE event_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("status", queued > 0 ? "DISPATCHED" : "NO_ENDPOINT")
                        .addValue("queued", queued)
                        .addValue("reference", reference));
        return Map.of("eventReference", reference, "eventType", eventType, "queuedDeliveries", queued);
    }

    public List<Map<String, Object>> requestLog(long merchantId, String projectReference, int limit) {
        long projectId = projectId(merchantId, projectReference);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT request_id AS requestId, http_method AS httpMethod, route_template AS routeTemplate, environment, "
                        + "response_status AS responseStatus, latency_ms AS latencyMs, error_code AS errorCode, created_at AS createdAt "
                        + "FROM developer_api_request_log WHERE merchant_id=:merchant_id AND project_id=:project_id ORDER BY id DESC LIMIT "
                        + safeLimit,
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("project_id", projectId));
    }

    @Transactional
    public void recordRequest(
            long merchantId,
            String projectReference,
            String serviceAccountReference,
            String requestId,
            String method,
            String route,
            String environment,
            Integer responseStatus,
            Long latencyMs,
            String errorCode) {
        long projectId = projectId(merchantId, projectReference);
        Long serviceAccountId =
                serviceAccountReference == null || serviceAccountReference.isBlank()
                        ? null
                        : serviceAccountId(merchantId, serviceAccountReference);
        jdbcTemplate.update(
                "INSERT INTO developer_api_request_log "
                        + "(merchant_id, project_id, service_account_id, request_id, http_method, route_template, environment, response_status, latency_ms, error_code) "
                        + "VALUES (:merchant_id, :project_id, :service_account_id, :request_id, :http_method, :route_template, :environment, :response_status, :latency_ms, :error_code)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("project_id", projectId)
                        .addValue("service_account_id", serviceAccountId)
                        .addValue("request_id", required(requestId, "requestId"))
                        .addValue("http_method", required(method, "method").toUpperCase(Locale.ROOT))
                        .addValue("route_template", required(route, "route"))
                        .addValue("environment", blankToNull(environment))
                        .addValue("response_status", responseStatus)
                        .addValue("latency_ms", latencyMs)
                        .addValue("error_code", blankToNull(errorCode)));
    }

    public Map<String, Object> readiness(long merchantId, String projectReference) {
        long projectId = projectId(merchantId, projectReference);
        List<Map<String, Object>> environments = jdbcTemplate.queryForList(
                "SELECT environment, status, production_eligible AS productionEligible, activated_at AS activatedAt "
                        + "FROM developer_project_environments WHERE project_id=:project_id ORDER BY environment",
                new MapSqlParameterSource("project_id", projectId));
        Integer activeServiceAccounts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM developer_service_accounts WHERE project_id=:project_id AND status='ACTIVE'",
                new MapSqlParameterSource("project_id", projectId), Integer.class);
        Integer activeCredentials = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM developer_credentials c JOIN developer_service_accounts a ON a.id=c.service_account_id "
                        + "WHERE a.project_id=:project_id AND c.status='ACTIVE' AND (c.expires_at IS NULL OR c.expires_at>CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("project_id", projectId), Integer.class);
        int webhookEndpoints = webhookService.listEndpoints(merchantId).size();
        boolean productionEntitled = entitlementService.hasEntitlement(
                merchantId, "DEVELOPER_CONTROL_PLANE", "PRODUCTION");
        return Map.of(
                "projectReference", projectReference,
                "environments", environments,
                "activeServiceAccounts", activeServiceAccounts == null ? 0 : activeServiceAccounts,
                "activeCredentials", activeCredentials == null ? 0 : activeCredentials,
                "webhookEndpoints", webhookEndpoints,
                "productionEntitled", productionEntitled,
                "sandboxReady", activeCredentials != null && activeCredentials > 0);
    }

    private Map<String, Object> project(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT project_reference AS projectReference, project_name AS projectName, description, status, created_by AS createdBy, "
                        + "created_at AS createdAt, updated_at AS updatedAt FROM developer_projects "
                        + "WHERE merchant_id=:merchant_id AND project_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference));
    }

    private Map<String, Object> serviceAccount(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT a.service_account_reference AS serviceAccountReference, a.display_name AS displayName, a.scopes_json AS scopes, "
                        + "a.status, a.created_by AS createdBy, a.created_at AS createdAt "
                        + "FROM developer_service_accounts a JOIN developer_projects p ON p.id=a.project_id "
                        + "WHERE p.merchant_id=:merchant_id AND a.service_account_reference=:reference",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", reference));
    }

    private long projectId(long merchantId, String reference) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM developer_projects WHERE merchant_id=:merchant_id AND project_reference=:reference AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", required(reference, "projectReference")),
                (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Developer project was not found or is inactive");
        }
        return rows.get(0);
    }

    private long serviceAccountId(long merchantId, String reference) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT a.id FROM developer_service_accounts a JOIN developer_projects p ON p.id=a.project_id "
                        + "WHERE p.merchant_id=:merchant_id AND a.service_account_reference=:reference AND a.status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", required(reference, "serviceAccountReference")),
                (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Service account was not found or is inactive");
        }
        return rows.get(0);
    }

    private List<String> normalizeScopes(List<String> scopes) {
        if (scopes == null || scopes.isEmpty()) {
            throw new PaymentGatewayException("At least one scope is required");
        }
        List<String> result = scopes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (result.isEmpty() || !ALLOWED_SCOPES.containsAll(result)) {
            throw new PaymentGatewayException("One or more developer scopes are unsupported");
        }
        return result;
    }

    private String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private String environment(String value) {
        String normalized = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private void requireMerchant(long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}