package net.citotech.cito.admin;

import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * P0 §2: real admin RBAC enforcement over the P0 role catalog.
 *
 * <p>Previously this service recorded an audit row but never verified that the acting principal's
 * role actually held the requested permission - any {@code ROLE_ADMIN} could perform any
 * privileged action. It now resolves the principal's roles from Spring authorities, checks them
 * against {@code admin_permissions} (seeded by V70 for {@code ADMIN} plus the nine P0 roles), and
 * refuses the action with {@link AccessDeniedException} unless at least one of those roles holds
 * the permission.
 *
 * <p>Backwards-compatible: the constructor signature, {@link #require}, {@link
 * #seedDefaultPermissions} and the {@code ADMIN} role keep working exactly as before, so existing
 * callers ({@link AdminImpersonationService}, {@link AdminOpsController}) are unaffected. The v2
 * admin surfaces remain additionally guarded by {@code SecurityConfig}'s path rule and the
 * method-level {@code @PreAuthorize} reinforcement - this is the permission-level layer those sit
 * on top of.
 */
@Service
public class AdminPermissionService {
    private static final String ROLE_PREFIX = "ROLE_";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminPermissionService(NamedParameterJdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    /**
     * Requires that at least one role held by the current principal has {@code permissionCode}
     * granted in {@code admin_permissions}. Records a full audit entry on every call (allowed and
     * denied paths both write an audit row so a denied attempt is as visible as a successful one).
     *
     * @throws AccessDeniedException if no admin role is held, or no held role has the permission
     */
    public void require(String permissionCode, String actionName, String resourceReference) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            auditService.record(permissionCode, actionName, resourceReference, "denied:no-authentication");
            throw new AccessDeniedException("Admin role is required");
        }

        List<String> roles = rolesOf(authentication);
        if (roles.isEmpty()) {
            auditService.record(permissionCode, actionName, resourceReference, "denied:no-admin-role");
            throw new AccessDeniedException("Admin role is required");
        }

        boolean granted = roles.stream().anyMatch(role -> hasPermission(role, permissionCode));
        String summary = granted
                ? "allowed;roles=" + String.join(",", roles)
                : "denied:permission-not-granted;roles=" + String.join(",", roles);
        auditService.record(permissionCode, actionName, resourceReference, summary);

        if (!granted) {
            throw new AccessDeniedException("Admin role " + String.join(",", roles)
                    + " does not hold permission " + permissionCode);
        }
    }

    /** Whether the given role currently has {@code permissionCode} in {@code admin_permissions}. */
    public boolean hasPermission(String roleName, String permissionCode) {
        String sql = "SELECT COUNT(*) FROM admin_permissions "
                + "WHERE role_name = :role_name AND permission_code = :permission_code";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("role_name", roleName);
        p.addValue("permission_code", permissionCode);
        Long count = jdbcTemplate.queryForObject(sql, p, Long.class);
        return count != null && count > 0;
    }

    /** Idempotently seeds the baseline permission codes for the legacy {@code ADMIN} role. */
    public void seedDefaultPermissions() {
        add("ADMIN", "BALANCE_BACKFILL");
        add("ADMIN", "CALLBACK_OPERATIONS");
        add("ADMIN", "RECONCILIATION_IMPORT");
        add("ADMIN", "RECONCILIATION_APPROVE");
        add("ADMIN", "PROVIDER_SANDBOX_VALIDATION");
    }

    private List<String> rolesOf(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(authority -> authority.startsWith(ROLE_PREFIX))
                .map(authority -> authority.substring(ROLE_PREFIX.length()))
                .toList();
    }

    private void add(String roleName, String permissionCode) {
        String sql = "INSERT IGNORE INTO admin_permissions (role_name, permission_code) VALUES (:role_name, :permission_code)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("role_name", roleName);
        p.addValue("permission_code", permissionCode);
        jdbcTemplate.update(sql, p);
    }
}
