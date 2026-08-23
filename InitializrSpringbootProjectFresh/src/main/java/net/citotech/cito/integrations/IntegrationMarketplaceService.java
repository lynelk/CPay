package net.citotech.cito.integrations;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.CitoEntitlementService;
import net.citotech.cito.webhook.MerchantWebhookService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationMarketplaceService {
    private static final Set<String> DIRECTIONS = Set.of("INBOUND", "OUTBOUND", "BIDIRECTIONAL");

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final CitoEntitlementService entitlementService;
    private final MerchantWebhookService webhookService;

    public IntegrationMarketplaceService(
            NamedParameterJdbcTemplate jdbcTemplate,
            CitoEntitlementService entitlementService,
            MerchantWebhookService webhookService) {
        this.jdbcTemplate = jdbcTemplate;
        this.entitlementService = entitlementService;
        this.webhookService = webhookService;
    }

    public List<Map<String, Object>> catalog() {
        return jdbcTemplate.queryForList(
                "SELECT c.connector_code AS connectorCode, c.connector_name AS connectorName, c.connector_category AS connectorCategory, "
                        + "c.description, c.publisher, c.auth_type AS authType, c.required_service_code AS requiredServiceCode, c.status, "
                        + "v.version_number AS currentVersion, v.manifest_json AS manifest "
                        + "FROM integration_connectors c LEFT JOIN integration_connector_versions v ON v.connector_id=c.id AND v.status='ACTIVE' "
                        + "WHERE c.status='ACTIVE' ORDER BY c.connector_category, c.connector_name, v.released_at DESC",
                new MapSqlParameterSource());
    }

    @Transactional
    public Map<String, Object> install(
            long merchantId,
            String connectorCode,
            String versionNumber,
            String environment,
            String displayName,
            String credentialReference,
            String configurationJson,
            String actor) {
        String env = environment(environment);
        entitlementService.requireEntitlement(merchantId, "INTEGRATIONS_MARKETPLACE", env);
        Connector connector = connector(connectorCode, versionNumber);
        if (connector.requiredServiceCode() != null && !connector.requiredServiceCode().isBlank()) {
            entitlementService.requireEntitlement(merchantId, connector.requiredServiceCode(), env);
        }
        String config = validateJson(configurationJson);
        String reference = reference("INT");
        jdbcTemplate.update(
                "INSERT INTO integration_installations "
                        + "(merchant_id, connector_id, connector_version_id, installation_reference, environment, display_name, credential_reference, configuration_json, status, installed_by) "
                        + "VALUES (:merchant_id, :connector_id, :version_id, :reference, :environment, :display_name, :credential_reference, :configuration_json, 'ACTIVE', :installed_by)",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("connector_id", connector.id())
                        .addValue("version_id", connector.versionId())
                        .addValue("reference", reference)
                        .addValue("environment", env)
                        .addValue("display_name", required(displayName, "displayName"))
                        .addValue("credential_reference", blankToNull(credentialReference))
                        .addValue("configuration_json", config)
                        .addValue("installed_by", blankToNull(actor)));
        return installation(merchantId, reference);
    }

    public List<Map<String, Object>> installations(long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT i.installation_reference AS installationReference, i.environment, i.display_name AS displayName, "
                        + "i.credential_reference AS credentialReference, i.configuration_json AS configuration, i.status, "
                        + "c.connector_code AS connectorCode, c.connector_name AS connectorName, v.version_number AS versionNumber, "
                        + "i.installed_at AS installedAt, i.updated_at AS updatedAt, i.uninstalled_at AS uninstalledAt "
                        + "FROM integration_installations i JOIN integration_connectors c ON c.id=i.connector_id "
                        + "JOIN integration_connector_versions v ON v.id=i.connector_version_id "
                        + "WHERE i.merchant_id=:merchant_id ORDER BY i.id DESC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public Map<String, Object> uninstall(long merchantId, String installationReference) {
        int updated = jdbcTemplate.update(
                "UPDATE integration_installations SET status='UNINSTALLED', uninstalled_at=CURRENT_TIMESTAMP "
                        + "WHERE merchant_id=:merchant_id AND installation_reference=:reference AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", required(installationReference, "installationReference")));
        if (updated == 0) {
            throw new PaymentGatewayException("Active integration installation was not found");
        }
        return installation(merchantId, installationReference);
    }

    @Transactional
    public Map<String, Object> addMapping(
            long merchantId,
            String installationReference,
            String objectType,
            String sourceField,
            String targetField,
            String transformation,
            String direction) {
        long installationId = installationId(merchantId, installationReference);
        String reference = reference("MAP");
        jdbcTemplate.update(
                "INSERT INTO integration_field_mappings "
                        + "(installation_id, mapping_reference, object_type, source_field, target_field, transformation, direction, status) "
                        + "VALUES (:installation_id, :reference, :object_type, :source_field, :target_field, :transformation, :direction, 'ACTIVE')",
                new MapSqlParameterSource()
                        .addValue("installation_id", installationId)
                        .addValue("reference", reference)
                        .addValue("object_type", required(objectType, "objectType").toUpperCase(Locale.ROOT))
                        .addValue("source_field", required(sourceField, "sourceField"))
                        .addValue("target_field", required(targetField, "targetField"))
                        .addValue("transformation", blankToNull(transformation))
                        .addValue("direction", direction(direction)));
        return Map.of("mappingReference", reference, "status", "ACTIVE");
    }

    public List<Map<String, Object>> mappings(long merchantId, String installationReference) {
        long installationId = installationId(merchantId, installationReference);
        return jdbcTemplate.queryForList(
                "SELECT mapping_reference AS mappingReference, object_type AS objectType, source_field AS sourceField, target_field AS targetField, "
                        + "transformation, direction, status, created_at AS createdAt FROM integration_field_mappings "
                        + "WHERE installation_id=:installation_id ORDER BY id",
                new MapSqlParameterSource("installation_id", installationId));
    }

    @Transactional
    public Map<String, Object> subscribeEvent(
            long merchantId, String installationReference, String eventType, String direction) {
        long installationId = installationId(merchantId, installationReference);
        String normalizedDirection = direction(direction);
        jdbcTemplate.update(
                "INSERT INTO integration_event_subscriptions (installation_id, event_type, direction, status) "
                        + "VALUES (:installation_id, :event_type, :direction, 'ACTIVE') "
                        + "ON DUPLICATE KEY UPDATE status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("installation_id", installationId)
                        .addValue("event_type", required(eventType, "eventType"))
                        .addValue("direction", normalizedDirection));
        return Map.of("eventType", eventType, "direction", normalizedDirection, "status", "ACTIVE");
    }

    @Transactional
    public Map<String, Object> queueJob(
            long merchantId,
            String installationReference,
            String idempotencyKey,
            String jobType,
            String objectReference,
            String payloadJson,
            int maxAttempts) {
        long installationId = installationId(merchantId, installationReference);
        String key = required(idempotencyKey, "idempotencyKey");
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT job_reference AS jobReference, status, attempt_count AS attemptCount, last_error AS lastError, created_at AS createdAt, completed_at AS completedAt "
                        + "FROM integration_sync_jobs WHERE installation_id=:installation_id AND idempotency_key=:idempotency_key",
                new MapSqlParameterSource()
                        .addValue("installation_id", installationId)
                        .addValue("idempotency_key", key));
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        String reference = reference("SYNC");
        jdbcTemplate.update(
                "INSERT INTO integration_sync_jobs "
                        + "(installation_id, job_reference, idempotency_key, job_type, object_reference, payload_json, status, max_attempts, next_attempt_at) "
                        + "VALUES (:installation_id, :reference, :idempotency_key, :job_type, :object_reference, :payload_json, 'QUEUED', :max_attempts, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource()
                        .addValue("installation_id", installationId)
                        .addValue("reference", reference)
                        .addValue("idempotency_key", key)
                        .addValue("job_type", required(jobType, "jobType").toUpperCase(Locale.ROOT))
                        .addValue("object_reference", blankToNull(objectReference))
                        .addValue("payload_json", validateJson(payloadJson))
                        .addValue("max_attempts", Math.max(1, Math.min(maxAttempts, 10))));
        return job(merchantId, reference);
    }

    public List<Map<String, Object>> jobs(long merchantId, String installationReference, int limit) {
        long installationId = installationId(merchantId, installationReference);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT job_reference AS jobReference, idempotency_key AS idempotencyKey, job_type AS jobType, object_reference AS objectReference, "
                        + "status, attempt_count AS attemptCount, max_attempts AS maxAttempts, next_attempt_at AS nextAttemptAt, last_error AS lastError, "
                        + "created_at AS createdAt, completed_at AS completedAt FROM integration_sync_jobs "
                        + "WHERE installation_id=:installation_id ORDER BY id DESC LIMIT " + safeLimit,
                new MapSqlParameterSource("installation_id", installationId));
    }

    @Scheduled(fixedDelayString = "${cpay.integrations.sync-delay-ms:60000}")
    @SchedulerLock(name = "integrationSyncJobs", lockAtMostFor = "PT5M", lockAtLeastFor = "PT5S")
    public void processDueJobs() {
        List<Long> ids = jdbcTemplate.query(
                "SELECT id FROM integration_sync_jobs WHERE status IN ('QUEUED','RETRY') AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY id LIMIT 100",
                new MapSqlParameterSource(),
                (rs, rowNum) -> rs.getLong("id"));
        for (Long id : ids) {
            processJob(id);
        }
    }

    void processJob(long jobId) {
        int claimed = jdbcTemplate.update(
                "UPDATE integration_sync_jobs SET status='PROCESSING' WHERE id=:id AND status IN ('QUEUED','RETRY')",
                new MapSqlParameterSource("id", jobId));
        if (claimed == 0) {
            return;
        }
        JobContext job = jobContext(jobId);
        int attempt = job.attemptCount() + 1;
        try {
            String summary;
            if ("GENERIC_WEBHOOK".equals(job.connectorCode())) {
                JSONObject config = new JSONObject(job.configurationJson());
                String eventType = config.optString("eventType", "payment.updated");
                int queued = webhookService.enqueue(
                        job.merchantId(), eventType, job.jobReference(), job.payloadJson());
                if (queued == 0) {
                    throw new PaymentGatewayException("No active merchant webhook endpoint is registered for " + eventType);
                }
                summary = "Queued " + queued + " webhook delivery task(s)";
            } else if ("GENERIC_ACCOUNTING_EXPORT".equals(job.connectorCode())) {
                summary = "Normalized accounting payload accepted for export";
            } else {
                throw new PaymentGatewayException("Connector execution is not available for " + job.connectorCode());
            }
            recordAttempt(jobId, attempt, "SUCCESS", "000", summary);
            jdbcTemplate.update(
                    "UPDATE integration_sync_jobs SET status='COMPLETED', attempt_count=:attempt_count, last_error=NULL, completed_at=CURRENT_TIMESTAMP WHERE id=:id",
                    new MapSqlParameterSource().addValue("id", jobId).addValue("attempt_count", attempt));
        } catch (RuntimeException e) {
            String message = safe(e.getMessage());
            recordAttempt(jobId, attempt, "FAILED", "CONNECTOR_ERROR", message);
            if (attempt < job.maxAttempts()) {
                long delaySeconds = Math.min(3600, 30L * (1L << Math.min(attempt - 1, 6)));
                jdbcTemplate.update(
                        "UPDATE integration_sync_jobs SET status='RETRY', attempt_count=:attempt_count, last_error=:last_error, "
                                + "next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL :delay_seconds SECOND) WHERE id=:id",
                        new MapSqlParameterSource()
                                .addValue("id", jobId)
                                .addValue("attempt_count", attempt)
                                .addValue("last_error", message)
                                .addValue("delay_seconds", delaySeconds));
            } else {
                jdbcTemplate.update(
                        "UPDATE integration_sync_jobs SET status='FAILED', attempt_count=:attempt_count, last_error=:last_error, completed_at=CURRENT_TIMESTAMP WHERE id=:id",
                        new MapSqlParameterSource()
                                .addValue("id", jobId)
                                .addValue("attempt_count", attempt)
                                .addValue("last_error", message));
            }
        }
    }

    private void recordAttempt(long jobId, int attempt, String outcome, String code, String summary) {
        jdbcTemplate.update(
                "INSERT INTO integration_sync_attempts (job_id, attempt_number, outcome, response_code, response_summary) "
                        + "VALUES (:job_id, :attempt_number, :outcome, :response_code, :response_summary)",
                new MapSqlParameterSource()
                        .addValue("job_id", jobId)
                        .addValue("attempt_number", attempt)
                        .addValue("outcome", outcome)
                        .addValue("response_code", code)
                        .addValue("response_summary", safe(summary)));
    }

    private Connector connector(String connectorCode, String versionNumber) {
        List<Connector> rows = jdbcTemplate.query(
                "SELECT c.id connector_id, c.connector_code, c.required_service_code, v.id version_id FROM integration_connectors c "
                        + "JOIN integration_connector_versions v ON v.connector_id=c.id "
                        + "WHERE c.connector_code=:connector_code AND c.status='ACTIVE' AND v.version_number=:version_number AND v.status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("connector_code", required(connectorCode, "connectorCode").toUpperCase(Locale.ROOT))
                        .addValue("version_number", required(versionNumber, "versionNumber")),
                (rs, rowNum) -> new Connector(
                        rs.getLong("connector_id"),
                        rs.getLong("version_id"),
                        rs.getString("connector_code"),
                        rs.getString("required_service_code")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Connector/version is not available");
        }
        return rows.get(0);
    }

    private JobContext jobContext(long id) {
        List<JobContext> rows = jdbcTemplate.query(
                "SELECT j.id, j.job_reference, j.payload_json, j.attempt_count, j.max_attempts, i.merchant_id, i.configuration_json, c.connector_code "
                        + "FROM integration_sync_jobs j JOIN integration_installations i ON i.id=j.installation_id "
                        + "JOIN integration_connectors c ON c.id=i.connector_id WHERE j.id=:id AND i.status='ACTIVE'",
                new MapSqlParameterSource("id", id),
                (rs, rowNum) -> new JobContext(
                        rs.getLong("id"), rs.getString("job_reference"), rs.getString("payload_json"),
                        rs.getInt("attempt_count"), rs.getInt("max_attempts"), rs.getLong("merchant_id"),
                        rs.getString("configuration_json"), rs.getString("connector_code")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Integration sync job is not executable");
        }
        return rows.get(0);
    }

    private long installationId(long merchantId, String reference) {
        List<Long> rows = jdbcTemplate.query(
                "SELECT id FROM integration_installations WHERE merchant_id=:merchant_id AND installation_reference=:reference AND status='ACTIVE'",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("reference", required(reference, "installationReference")),
                (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Active integration installation was not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> installation(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT i.installation_reference AS installationReference, i.environment, i.display_name AS displayName, "
                        + "i.credential_reference AS credentialReference, i.configuration_json AS configuration, i.status, "
                        + "c.connector_code AS connectorCode, c.connector_name AS connectorName, v.version_number AS versionNumber, "
                        + "i.installed_at AS installedAt, i.updated_at AS updatedAt FROM integration_installations i "
                        + "JOIN integration_connectors c ON c.id=i.connector_id JOIN integration_connector_versions v ON v.id=i.connector_version_id "
                        + "WHERE i.merchant_id=:merchant_id AND i.installation_reference=:reference",
                new MapSqlParameterSource().addValue("merchant_id", merchantId).addValue("reference", reference));
    }

    private Map<String, Object> job(long merchantId, String reference) {
        return jdbcTemplate.queryForMap(
                "SELECT j.job_reference AS jobReference, j.idempotency_key AS idempotencyKey, j.job_type AS jobType, j.object_reference AS objectReference, "
                        + "j.status, j.attempt_count AS attemptCount, j.max_attempts AS maxAttempts, j.next_attempt_at AS nextAttemptAt, j.last_error AS lastError, "
                        + "j.created_at AS createdAt, j.completed_at AS completedAt FROM integration_sync_jobs j "
                        + "JOIN integration_installations i ON i.id=j.installation_id WHERE i.merchant_id=:merchant_id AND j.job_reference=:reference",
                new MapSqlParameterSource().addValue("merchant_id", merchantId).addValue("reference", reference));
    }

    private String validateJson(String value) {
        String json = value == null || value.isBlank() ? "{}" : value.trim();
        try {
            new JSONObject(json);
            return json;
        } catch (Exception e) {
            throw new PaymentGatewayException("JSON configuration/payload must be a valid object");
        }
    }

    private String direction(String value) {
        String normalized = required(value, "direction").toUpperCase(Locale.ROOT);
        if (!DIRECTIONS.contains(normalized)) {
            throw new PaymentGatewayException("direction must be INBOUND, OUTBOUND, or BIDIRECTIONAL");
        }
        return normalized;
    }

    private String environment(String value) {
        String normalized = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    private String reference(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "Connector execution failed";
        }
        String trimmed = value.trim();
        return trimmed.length() > 950 ? trimmed.substring(0, 950) : trimmed;
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

    private record Connector(long id, long versionId, String connectorCode, String requiredServiceCode) {}
    private record JobContext(long id, String jobReference, String payloadJson, int attemptCount, int maxAttempts, long merchantId, String configurationJson, String connectorCode) {}
}