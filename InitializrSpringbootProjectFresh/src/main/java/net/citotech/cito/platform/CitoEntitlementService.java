package net.citotech.cito.platform;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CitoEntitlementService {
    private static final Set<String> ENVIRONMENTS = Set.of("SANDBOX", "PRODUCTION");
    private static final Set<String> ENTITLEMENT_STATUSES =
            Set.of("REQUESTED", "APPROVED", "ACTIVE", "SUSPENDED", "REVOKED");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CitoEntitlementService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long ensureMerchantOrganization(long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("organization_reference", "MERCHANT-" + merchantId)
                        .addValue("organization_name", "Merchant " + merchantId);
        jdbcTemplate.update(
                "INSERT INTO cito_organizations (organization_reference, merchant_id, organization_name, status) "
                        + "VALUES (:organization_reference, :merchant_id, :organization_name, 'ACTIVE') "
                        + "ON DUPLICATE KEY UPDATE updated_at=CURRENT_TIMESTAMP",
                p);
        Long organizationId =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM cito_organizations WHERE merchant_id=:merchant_id",
                        p,
                        Long.class);
        if (organizationId == null) {
            throw new PaymentGatewayException("Unable to resolve Cito organization");
        }
        seedDefaultSandboxEntitlements(organizationId);
        return organizationId;
    }

    public List<Map<String, Object>> serviceCatalog() {
        return jdbcTemplate.queryForList(
                "SELECT service_code AS serviceCode, service_name AS serviceName, description, status, "
                        + "default_sandbox_access AS defaultSandboxAccess "
                        + "FROM cito_service_catalog ORDER BY service_name",
                new MapSqlParameterSource());
    }

    public List<Map<String, Object>> entitlementsForMerchant(long merchantId) {
        long organizationId = ensureMerchantOrganization(merchantId);
        return jdbcTemplate.queryForList(
                "SELECT e.id, e.service_code AS serviceCode, s.service_name AS serviceName, "
                        + "e.environment, e.status, e.plan_code AS planCode, e.starts_at AS startsAt, "
                        + "e.ends_at AS endsAt, e.approved_by AS approvedBy, e.updated_at AS updatedAt "
                        + "FROM cito_service_entitlements e "
                        + "JOIN cito_service_catalog s ON s.service_code=e.service_code "
                        + "WHERE e.organization_id=:organization_id ORDER BY s.service_name, e.environment",
                new MapSqlParameterSource("organization_id", organizationId));
    }

    public boolean hasEntitlement(long merchantId, String serviceCode, String environment) {
        long organizationId = ensureMerchantOrganization(merchantId);
        String service = normalizeService(serviceCode);
        String env = normalizeEnvironment(environment);
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM cito_service_entitlements "
                                + "WHERE organization_id=:organization_id AND service_code=:service_code "
                                + "AND environment=:environment AND status='ACTIVE' "
                                + "AND (starts_at IS NULL OR starts_at<=CURRENT_TIMESTAMP) "
                                + "AND (ends_at IS NULL OR ends_at>CURRENT_TIMESTAMP)",
                        new MapSqlParameterSource()
                                .addValue("organization_id", organizationId)
                                .addValue("service_code", service)
                                .addValue("environment", env),
                        Integer.class);
        return count != null && count > 0;
    }

    public void requireEntitlement(long merchantId, String serviceCode, String environment) {
        if (!hasEntitlement(merchantId, serviceCode, environment)) {
            throw new PaymentGatewayException(
                    "Cito service is not active for this organization in "
                            + normalizeEnvironment(environment));
        }
    }

    @Transactional
    public Map<String, Object> setEntitlement(
            long merchantId,
            String serviceCode,
            String environment,
            String status,
            String planCode,
            Instant startsAt,
            Instant endsAt,
            String actor) {
        long organizationId = ensureMerchantOrganization(merchantId);
        String service = normalizeService(serviceCode);
        String env = normalizeEnvironment(environment);
        String normalizedStatus = normalizeStatus(status);
        requireServiceExists(service);
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new PaymentGatewayException("endsAt must be after startsAt");
        }

        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("organization_id", organizationId)
                        .addValue("service_code", service)
                        .addValue("environment", env)
                        .addValue("status", normalizedStatus)
                        .addValue("plan_code", blankToNull(planCode))
                        .addValue("starts_at", startsAt == null ? null : Timestamp.from(startsAt))
                        .addValue("ends_at", endsAt == null ? null : Timestamp.from(endsAt))
                        .addValue("approved_by", blankToNull(actor));
        jdbcTemplate.update(
                "INSERT INTO cito_service_entitlements "
                        + "(organization_id, service_code, environment, status, plan_code, starts_at, ends_at, approved_by) "
                        + "VALUES (:organization_id, :service_code, :environment, :status, :plan_code, :starts_at, :ends_at, :approved_by) "
                        + "ON DUPLICATE KEY UPDATE status=VALUES(status), plan_code=VALUES(plan_code), "
                        + "starts_at=VALUES(starts_at), ends_at=VALUES(ends_at), approved_by=VALUES(approved_by), "
                        + "updated_at=CURRENT_TIMESTAMP",
                p);
        audit(
                organizationId,
                "ENTITLEMENT_UPDATED",
                service + ":" + env,
                actor,
                "{\"status\":\"" + normalizedStatus + "\"}");
        return entitlement(organizationId, service, env);
    }

    @Transactional
    public Map<String, Object> createAccessReview(
            long merchantId, Instant dueAt, String requestedBy, String notes) {
        long organizationId = ensureMerchantOrganization(merchantId);
        String reference = "CAR-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("review_reference", reference)
                        .addValue("organization_id", organizationId)
                        .addValue("requested_by", blankToNull(requestedBy))
                        .addValue("due_at", dueAt == null ? null : Timestamp.from(dueAt))
                        .addValue("notes", blankToNull(notes));
        jdbcTemplate.update(
                "INSERT INTO cito_access_reviews "
                        + "(review_reference, organization_id, status, requested_by, due_at, notes) "
                        + "VALUES (:review_reference, :organization_id, 'PENDING_REVIEW', :requested_by, :due_at, :notes)",
                p);
        audit(organizationId, "ACCESS_REVIEW_CREATED", reference, requestedBy, null);
        return review(reference);
    }

    @Transactional
    public Map<String, Object> completeAccessReview(
            String reviewReference, String status, String reviewer, String notes) {
        String normalized = normalizeReviewStatus(status);
        MapSqlParameterSource p =
                new MapSqlParameterSource()
                        .addValue("review_reference", required(reviewReference, "reviewReference"))
                        .addValue("status", normalized)
                        .addValue("reviewer", required(reviewer, "reviewer"))
                        .addValue("notes", blankToNull(notes));
        int updated =
                jdbcTemplate.update(
                        "UPDATE cito_access_reviews SET status=:status, reviewer=:reviewer, notes=:notes, "
                                + "completed_at=CURRENT_TIMESTAMP WHERE review_reference=:review_reference "
                                + "AND status='PENDING_REVIEW'",
                        p);
        if (updated == 0) {
            throw new PaymentGatewayException("Access review was not found or is already complete");
        }
        Map<String, Object> result = review(reviewReference);
        audit(
                ((Number) result.get("organizationId")).longValue(),
                "ACCESS_REVIEW_COMPLETED",
                reviewReference,
                reviewer,
                "{\"status\":\"" + normalized + "\"}");
        return result;
    }

    public List<Map<String, Object>> accessEvents(long merchantId, int limit) {
        long organizationId = ensureMerchantOrganization(merchantId);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT id, event_type AS eventType, target_reference AS targetReference, "
                        + "actor_reference AS actorReference, detail_json AS detailJson, created_at AS createdAt "
                        + "FROM cito_access_events WHERE organization_id=:organization_id "
                        + "ORDER BY id DESC LIMIT "
                        + safeLimit,
                new MapSqlParameterSource("organization_id", organizationId));
    }

    private void seedDefaultSandboxEntitlements(long organizationId) {
        jdbcTemplate.update(
                "INSERT IGNORE INTO cito_service_entitlements "
                        + "(organization_id, service_code, environment, status, plan_code, approved_by) "
                        + "SELECT :organization_id, service_code, 'SANDBOX', 'ACTIVE', 'SANDBOX', 'SYSTEM' "
                        + "FROM cito_service_catalog WHERE status='ACTIVE' AND default_sandbox_access='YES'",
                new MapSqlParameterSource("organization_id", organizationId));
    }

    private Map<String, Object> entitlement(long organizationId, String service, String env) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT e.id, e.organization_id AS organizationId, e.service_code AS serviceCode, "
                                + "s.service_name AS serviceName, e.environment, e.status, e.plan_code AS planCode, "
                                + "e.starts_at AS startsAt, e.ends_at AS endsAt, e.approved_by AS approvedBy, "
                                + "e.updated_at AS updatedAt FROM cito_service_entitlements e "
                                + "JOIN cito_service_catalog s ON s.service_code=e.service_code "
                                + "WHERE e.organization_id=:organization_id AND e.service_code=:service_code "
                                + "AND e.environment=:environment",
                        new MapSqlParameterSource()
                                .addValue("organization_id", organizationId)
                                .addValue("service_code", service)
                                .addValue("environment", env));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Entitlement was not found");
        }
        return rows.get(0);
    }

    private Map<String, Object> review(String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT review_reference AS reviewReference, organization_id AS organizationId, status, "
                                + "requested_by AS requestedBy, reviewer, due_at AS dueAt, completed_at AS completedAt, "
                                + "notes, created_at AS createdAt FROM cito_access_reviews "
                                + "WHERE review_reference=:review_reference",
                        new MapSqlParameterSource("review_reference", reference));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Access review was not found");
        }
        return rows.get(0);
    }

    private void requireServiceExists(String serviceCode) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM cito_service_catalog WHERE service_code=:service_code AND status='ACTIVE'",
                        new MapSqlParameterSource("service_code", serviceCode),
                        Integer.class);
        if (count == null || count == 0) {
            throw new PaymentGatewayException("Unknown or inactive Cito service");
        }
    }

    private void audit(
            long organizationId, String eventType, String target, String actor, String detailJson) {
        jdbcTemplate.update(
                "INSERT INTO cito_access_events "
                        + "(organization_id, event_type, target_reference, actor_reference, detail_json) "
                        + "VALUES (:organization_id, :event_type, :target_reference, :actor_reference, :detail_json)",
                new MapSqlParameterSource()
                        .addValue("organization_id", organizationId)
                        .addValue("event_type", eventType)
                        .addValue("target_reference", blankToNull(target))
                        .addValue("actor_reference", blankToNull(actor))
                        .addValue("detail_json", detailJson));
    }

    private String normalizeService(String value) {
        return required(value, "serviceCode").toUpperCase(Locale.ROOT);
    }

    private String normalizeEnvironment(String value) {
        String normalized = required(value, "environment").toUpperCase(Locale.ROOT);
        if (!ENVIRONMENTS.contains(normalized)) {
            throw new PaymentGatewayException("environment must be SANDBOX or PRODUCTION");
        }
        return normalized;
    }

    private String normalizeStatus(String value) {
        String normalized = required(value, "status").toUpperCase(Locale.ROOT);
        if (!ENTITLEMENT_STATUSES.contains(normalized)) {
            throw new PaymentGatewayException("Unsupported entitlement status");
        }
        return normalized;
    }

    private String normalizeReviewStatus(String value) {
        String normalized = required(value, "status").toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVED", "REJECTED").contains(normalized)) {
            throw new PaymentGatewayException("Access review status must be APPROVED or REJECTED");
        }
        return normalized;
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