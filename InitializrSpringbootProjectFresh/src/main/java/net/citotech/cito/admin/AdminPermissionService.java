package net.citotech.cito.admin;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AdminPermissionService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AdminAuditService auditService;

    public AdminPermissionService(
            NamedParameterJdbcTemplate jdbcTemplate, AdminAuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditService = auditService;
    }

    public void require(String permissionCode, String actionName, String resourceReference) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !hasAdmin(authentication)) {
            throw new AccessDeniedException("Admin role is required");
        }
        Integer granted =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM admin_permissions WHERE role_name='ADMIN' AND permission_code=:permission",
                        new MapSqlParameterSource("permission", permissionCode),
                        Integer.class);
        if (granted == null || granted == 0) {
            auditService.record(permissionCode, actionName, resourceReference, "denied");
            throw new AccessDeniedException("Admin permission is required: " + permissionCode);
        }
        auditService.record(permissionCode, actionName, resourceReference, "allowed");
    }

    public void seedDefaultPermissions() {
        add("ADMIN", "BALANCE_BACKFILL");
        add("ADMIN", "CALLBACK_OPERATIONS");
        add("ADMIN", "RECONCILIATION_IMPORT");
        add("ADMIN", "RECONCILIATION_APPROVE");
        add("ADMIN", "PROVIDER_SANDBOX_VALIDATION");
        add("ADMIN", "PAYMENT_BALANCE_VIEW");
        add("ADMIN", "PAYMENT_BALANCE_REFRESH");
        add("ADMIN", "SHARED_PAYMENT_ENTITLEMENT_MANAGE");
        add("ADMIN", "SHARED_PAYMENT_LIMIT_APPROVE");
        add("ADMIN", "LIVE_COLLECTION_TEST");
        add("ADMIN", "LIVE_DISBURSEMENT_TEST");
        add("ADMIN", "LIVE_DISBURSEMENT_APPROVE");
        add("ADMIN", "PROVIDER_CREDENTIAL_MANAGE");
        add("ADMIN", "PROVIDER_CREDENTIAL_APPROVE");
        add("ADMIN", "RECONCILIATION_VIEW");
        add("ADMIN", "RECONCILIATION_MANAGE");
    }

    private boolean hasAdmin(Authentication authentication) {
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if ("ROLE_ADMIN".equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    private void add(String roleName, String permissionCode) {
        String sql =
                "INSERT IGNORE INTO admin_permissions (role_name, permission_code) VALUES (:role_name, :permission_code)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("role_name", roleName);
        p.addValue("permission_code", permissionCode);
        jdbcTemplate.update(sql, p);
    }
}
