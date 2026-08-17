package net.citotech.cito.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * P0 §2: maker-checker approval-request lifecycle.
 *
 * <p>A privileged action that requires maker-checker (settlement approval, daily close, manual
 * finance adjustment, callback secret rotation, high-value payout release, compliance case closure,
 * admin role change, production cap removal, provider production enablement, merchant production
 * activation - see {@code admin_access_matrix}) must first be recorded here as a {@code
 * PENDING_APPROVAL} request by one actor (the maker), then approved or rejected by a different
 * actor (the checker). The maker can never decide their own request, which is the core separation
 * P0 §2 exists to enforce.
 *
 * <p>Each request carries the target resource, the action payload, an optional TTL, and the
 * previous/new state hashes required by the P0 audit field list; every transition writes a full
 * audit entry via {@link AdminAuditService}.
 */
@Service
public class AdminApprovalService {
    static final String STATUS_PENDING = "PENDING_APPROVAL";
    static final String STATUS_APPROVED = "APPROVED";
    static final String STATUS_REJECTED = "REJECTED";
    static final String STATUS_CANCELLED = "CANCELLED";
    static final String STATUS_EXPIRED = "EXPIRED";

    public static final String PERMISSION_CREATE = "APPROVAL_REQUEST_CREATE";
    public static final String PERMISSION_APPROVE = "APPROVAL_REQUEST_APPROVE";
    public static final String PERMISSION_REJECT = "APPROVAL_REQUEST_REJECT";
    public static final String PERMISSION_READ = "APPROVAL_REQUEST_READ";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminPermissionService permissions;
    private final AdminAuditService auditService;

    public AdminApprovalService(
            NamedParameterJdbcTemplate jdbcTemplate,
            AdminPermissionService permissions,
            AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.permissions = permissions;
        this.auditService = auditService;
    }

    /**
     * Maker step: record a privileged action for checker approval. The acting principal must hold
     * {@link #PERMISSION_CREATE}; a non-blank {@code approvalType}, {@code resourceType} and {@code
     * resourceId} are required; the payload is stored for the checker's review.
     *
     * @return the request id
     */
    @Transactional
    public long create(
            String approvalType,
            String resourceType,
            String resourceId,
            Map<String, Object> payload,
            String previousStateHash,
            String newStateHash,
            String requestId,
            String expiresInHours) {
        permissions.require(
                PERMISSION_CREATE, "approval-request-create", resourceType + ":" + resourceId);

        String trimmedType = required(approvalType, "approvalType");
        String trimmedResourceType = required(resourceType, "resourceType");
        String trimmedResourceId = required(resourceId, "resourceId");

        Instant now = Instant.now();
        String requestReference = "ARQ-" + now.toEpochMilli() + "-" + safeToken(trimmedResourceId);

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("request_reference", requestReference);
        p.addValue("approval_type", trimmedType);
        p.addValue("request_status", STATUS_PENDING);
        p.addValue("resource_type", trimmedResourceType);
        p.addValue("resource_id", trimmedResourceId);
        p.addValue("request_payload", toJson(payload));
        p.addValue("requested_by", currentActor());
        p.addValue("previous_state_hash", previousStateHash);
        p.addValue("new_state_hash", newStateHash);
        p.addValue("request_id", requestId);
        p.addValue("expires_at", expiry(expiresInHours));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO approval_requests "
                        + "(request_reference, approval_type, request_status, resource_type, resource_id, "
                        + " request_payload, requested_by, previous_state_hash, new_state_hash, request_id, expires_at) "
                        + "VALUES (:request_reference, :approval_type, :request_status, :resource_type, :resource_id, "
                        + " :request_payload, :requested_by, :previous_state_hash, :new_state_hash, :request_id, :expires_at)",
                p,
                keyHolder,
                new String[] {"id"});
        long requestIdValue = keyHolder.getKey().longValue();

        auditService.record(
                PERMISSION_CREATE,
                "APPROVAL_REQUEST_CREATE",
                trimmedResourceType + ":" + trimmedResourceId,
                "approval_type="
                        + trimmedType
                        + "; request_reference="
                        + requestReference
                        + "; requested_by="
                        + currentActor()
                        + "; expires_in_hours="
                        + expiresInHours,
                new AdminAuditService.AuditContext(
                        null,
                        trimmedResourceType,
                        trimmedResourceId,
                        previousStateHash,
                        newStateHash,
                        null,
                        requestId));

        return requestIdValue;
    }

    /**
     * Checker step: approve a pending request. Requires {@link #PERMISSION_APPROVE} and a checker
     * different from the request's maker. Idempotent for already-decided requests: approving an
     * approved request is a no-op returning the stored state; approving a rejected/cancelled/
     * expired request is refused.
     */
    @Transactional
    public Map<String, Object> approve(long requestId, String checker, String note) {
        permissions.require(
                PERMISSION_APPROVE, "approval-request-approve", "approval:" + requestId);

        Map<String, Object> existing = findById(requestId);
        if (existing == null) {
            throw new PaymentGatewayException("approval request " + requestId + " not found");
        }

        String status = (String) existing.get("request_status");
        if (STATUS_APPROVED.equals(status)) {
            return existing;
        }
        if (!STATUS_PENDING.equals(status)) {
            throw new PaymentGatewayException(
                    "approval request " + requestId + " is " + status + " and cannot be approved");
        }
        if (expired(existing)) {
            String actor = effectiveActor(checker);
            markExpired(requestId, actor);
            throw new PaymentGatewayException("approval request " + requestId + " has expired");
        }

        String maker = (String) existing.get("requested_by");
        String actor = effectiveActor(checker);
        if (maker != null && maker.equals(actor)) {
            throw new PaymentGatewayException("a maker cannot approve their own approval request");
        }

        MapSqlParameterSource update = new MapSqlParameterSource();
        update.addValue("id", requestId);
        update.addValue("approved_by", actor);
        update.addValue("review_note", note);
        jdbcTemplate.update(
                "UPDATE approval_requests SET request_status = 'APPROVED', approved_by = :approved_by, "
                        + "approved_at = CURRENT_TIMESTAMP, review_note = :review_note "
                        + "WHERE id = :id AND request_status = 'PENDING_APPROVAL'",
                update);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("requestStatus", STATUS_APPROVED);
        result.put("approvedBy", actor);
        result.put("resourceType", existing.get("resource_type"));
        result.put("resourceId", existing.get("resource_id"));
        result.put("newStateHash", existing.get("new_state_hash"));

        auditService.record(
                PERMISSION_APPROVE,
                "APPROVAL_REQUEST_APPROVE",
                existing.get("resource_type") + ":" + existing.get("resource_id"),
                "approval_request_id="
                        + requestId
                        + "; approved_by="
                        + actor
                        + "; review_note="
                        + note,
                new AdminAuditService.AuditContext(
                        null,
                        (String) existing.get("resource_type"),
                        (String) existing.get("resource_id"),
                        (String) existing.get("previous_state_hash"),
                        (String) existing.get("new_state_hash"),
                        note,
                        (String) existing.get("request_id")));

        return result;
    }

    /**
     * Checker step: reject a pending request. Requires {@link #PERMISSION_REJECT}, a checker
     * different from the maker, and a non-blank rejection reason. Refused once the request is no
     * longer pending.
     */
    @Transactional
    public Map<String, Object> reject(long requestId, String checker, String reason) {
        permissions.require(PERMISSION_REJECT, "approval-request-reject", "approval:" + requestId);

        Map<String, Object> existing = findById(requestId);
        if (existing == null) {
            throw new PaymentGatewayException("approval request " + requestId + " not found");
        }

        String status = (String) existing.get("request_status");
        if (!STATUS_PENDING.equals(status)) {
            throw new PaymentGatewayException(
                    "approval request " + requestId + " is " + status + " and cannot be rejected");
        }
        if (expired(existing)) {
            String actor = effectiveActor(checker);
            markExpired(requestId, actor);
            throw new PaymentGatewayException("approval request " + requestId + " has expired");
        }

        String maker = (String) existing.get("requested_by");
        String actor = effectiveActor(checker);
        if (maker != null && maker.equals(actor)) {
            throw new PaymentGatewayException("a maker cannot reject their own approval request");
        }

        String trimmedReason = required(reason, "reason");

        MapSqlParameterSource update = new MapSqlParameterSource();
        update.addValue("id", requestId);
        update.addValue("rejected_by", actor);
        update.addValue("rejection_reason", trimmedReason);
        jdbcTemplate.update(
                "UPDATE approval_requests SET request_status = 'REJECTED', rejected_by = :rejected_by, "
                        + "rejected_at = CURRENT_TIMESTAMP, rejection_reason = :rejection_reason "
                        + "WHERE id = :id AND request_status = 'PENDING_APPROVAL'",
                update);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestId", requestId);
        result.put("requestStatus", STATUS_REJECTED);
        result.put("rejectedBy", actor);
        result.put("resourceType", existing.get("resource_type"));
        result.put("resourceId", existing.get("resource_id"));

        auditService.record(
                PERMISSION_REJECT,
                "APPROVAL_REQUEST_REJECT",
                existing.get("resource_type") + ":" + existing.get("resource_id"),
                "approval_request_id="
                        + requestId
                        + "; rejected_by="
                        + actor
                        + "; reason="
                        + trimmedReason,
                new AdminAuditService.AuditContext(
                        null,
                        (String) existing.get("resource_type"),
                        (String) existing.get("resource_id"),
                        (String) existing.get("previous_state_hash"),
                        (String) existing.get("new_state_hash"),
                        trimmedReason,
                        (String) existing.get("request_id")));

        return result;
    }

    /**
     * Generic maker-checker gate: refuses an action on {@code resourceType:resourceId} if one is
     * pending.
     */
    @Transactional
    public void requireNoPendingFor(String resourceType, String resourceId, String actionName) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("resource_type", resourceType);
        p.addValue("resource_id", resourceId);
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM approval_requests WHERE resource_type = :resource_type "
                                + "AND resource_id = :resource_id AND request_status = 'PENDING_APPROVAL'",
                        p,
                        Long.class);
        if (count != null && count > 0) {
            throw new PaymentGatewayException(
                    actionName
                            + " blocked: a pending approval request already exists for "
                            + resourceType
                            + ":"
                            + resourceId);
        }
    }

    public Map<String, Object> get(long requestId) {
        permissions.require(PERMISSION_READ, "approval-request-read", "approval:" + requestId);
        Map<String, Object> existing = findById(requestId);
        if (existing == null) {
            throw new PaymentGatewayException("approval request " + requestId + " not found");
        }
        return existing;
    }

    public List<Map<String, Object>> list(String status, String approvalType, int limit) {
        permissions.require(PERMISSION_READ, "approval-request-list", "approval_requests");
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql =
                "SELECT id, request_reference, approval_type, request_status, resource_type, resource_id, "
                        + "requested_by, requested_at, approved_by, approved_at, rejected_by, rejected_at, "
                        + "rejection_reason, review_note, previous_state_hash, new_state_hash, request_id, expires_at "
                        + "FROM approval_requests";
        List<String> conditions = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            conditions.add("request_status = :status");
            p.addValue("status", status.trim().toUpperCase());
        }
        if (approvalType != null && !approvalType.isBlank()) {
            conditions.add("approval_type = :approval_type");
            p.addValue("approval_type", approvalType.trim());
        }
        if (!conditions.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conditions);
        }
        int boundedLimit = limit <= 0 ? 100 : Math.min(limit, 500);
        sql += " ORDER BY requested_at DESC LIMIT " + boundedLimit;
        return jdbcTemplate.queryForList(sql, p);
    }

    private Map<String, Object> findById(long requestId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id, request_reference, approval_type, request_status, resource_type, resource_id, "
                                + "request_payload, requested_by, requested_at, approved_by, approved_at, rejected_by, "
                                + "rejected_at, rejection_reason, review_note, previous_state_hash, new_state_hash, "
                                + "request_id, expires_at FROM approval_requests WHERE id = :id",
                        new MapSqlParameterSource("id", requestId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean expired(Map<String, Object> row) {
        Object expiresAt = row.get("expires_at");
        if (expiresAt == null) {
            return false;
        }
        Instant expiry =
                expiresAt instanceof Timestamp timestamp
                        ? timestamp.toInstant()
                        : Instant.parse(expiresAt.toString());
        return expiry.isBefore(Instant.now());
    }

    private void markExpired(long requestId, String actor) {
        jdbcTemplate.update(
                "UPDATE approval_requests SET request_status = 'EXPIRED' "
                        + "WHERE id = :id AND request_status = 'PENDING_APPROVAL'",
                new MapSqlParameterSource("id", requestId));
        auditService.record(
                PERMISSION_READ,
                "APPROVAL_REQUEST_EXPIRE",
                "approval:" + requestId,
                "expired_by=" + actor);
    }

    private String effectiveActor(String checker) {
        if (checker != null && !checker.isBlank()) {
            return checker.trim();
        }
        return currentActor();
    }

    private String currentActor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "system";
        }
        return authentication.getName();
    }

    private Timestamp expiry(String expiresInHours) {
        if (expiresInHours == null || expiresInHours.isBlank()) {
            return null;
        }
        try {
            int hours = Integer.parseInt(expiresInHours.trim());
            if (hours <= 0 || hours > 720) {
                throw new IllegalArgumentException("expiresInHours must be between 1 and 720");
            }
            return Timestamp.from(Instant.now().plusSeconds(hours * 3600L));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("expiresInHours must be a whole number of hours");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private String safeToken(String value) {
        return Integer.toHexString(value.hashCode());
    }

    private String toJson(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append('"').append(entry.getKey().replace("\"", "\\\"")).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(value.toString().replace("\"", "\\\"")).append('"');
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
