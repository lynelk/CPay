package net.citotech.cito.admin;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Append-only enriched audit trail for privileged admin actions.
 *
 * <p>P0 §2 requires every privileged action to record actor id, role, action, affected resource
 * type/id, previous/new state hashes, reason, request id and timestamp. The {@code
 * admin_audit_events} columns for that list were added by V70; this service exposes a
 * backward-compatible {@link #record(String, String, String, String)} plus a richer overload that
 * takes an {@link AuditContext} so callers (admin role changes, approval-request decisions) can
 * write the full field set. Rows remain append-only; V28 already blocks UPDATE/DELETE on the legacy
 * audit tables with triggers, and this table follows the same no-mutation convention.
 */
@Service
public class AdminAuditService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminAuditService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Backward-compatible overload: writes the core fields with the enriched columns NULL. */
    public void record(
            String permissionCode,
            String actionName,
            String resourceReference,
            String requestSummary) {
        record(permissionCode, actionName, resourceReference, requestSummary, null);
    }

    /**
     * Writes a full audit entry. When {@code auditContext} is null the enriched columns are NULL,
     * keeping the legacy behaviour.
     */
    public void record(
            String permissionCode,
            String actionName,
            String resourceReference,
            String requestSummary,
            AuditContext auditContext) {
        String actor = currentActor();
        String sql =
                "INSERT INTO admin_audit_events "
                        + "(actor, actor_role, permission_code, action_name, resource_reference, "
                        + " resource_type, resource_id, previous_state_hash, new_state_hash, "
                        + " reason_text, request_id, request_summary) "
                        + "VALUES (:actor, :actor_role, :permission_code, :action_name, :resource_reference, "
                        + " :resource_type, :resource_id, :previous_state_hash, :new_state_hash, "
                        + " :reason_text, :request_id, :request_summary)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("actor", actor);
        p.addValue("actor_role", auditContext == null ? null : auditContext.actorRole());
        p.addValue("permission_code", permissionCode);
        p.addValue("action_name", actionName);
        p.addValue("resource_reference", resourceReference);
        p.addValue("resource_type", auditContext == null ? null : auditContext.resourceType());
        p.addValue("resource_id", auditContext == null ? null : auditContext.resourceId());
        p.addValue(
                "previous_state_hash",
                auditContext == null ? null : auditContext.previousStateHash());
        p.addValue("new_state_hash", auditContext == null ? null : auditContext.newStateHash());
        p.addValue("reason_text", auditContext == null ? null : auditContext.reasonText());
        p.addValue("request_id", auditContext == null ? null : auditContext.requestId());
        p.addValue("request_summary", requestSummary);
        jdbcTemplate.update(sql, p);
    }

    private String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "system";
        }
        return authentication.getName();
    }

    /** Optional P0 audit-field enrichment carried alongside a core audit record. */
    public record AuditContext(
            String actorRole,
            String resourceType,
            String resourceId,
            String previousStateHash,
            String newStateHash,
            String reasonText,
            String requestId) {}
}
