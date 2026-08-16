package net.citotech.cito.admin;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * P0 §2 read surface for the admin RBAC catalog: the role list, the permission grants, and the
 * authorization matrix from {@code admin_access_matrix}. Read-only by design - mutations of the
 * catalog are executed by {@code AdminPermissionService.seedDefaultPermissions()} (idempotent
 * seeds) or directly by a SECURITY_ADMIN through the target application (the matrix is a
 * deployment-time policy table).
 */
@Service
public class AdminAccessService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AdminAccessService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> listRoles() {
        return jdbcTemplate.queryForList(
                "SELECT role_name, description FROM admin_roles ORDER BY role_name",
                new MapSqlParameterSource());
    }

    public List<Map<String, Object>> listPermissions(String roleName) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String sql = "SELECT role_name, permission_code FROM admin_permissions";
        if (roleName != null && !roleName.isBlank()) {
            sql += " WHERE role_name = :role_name";
            p.addValue("role_name", roleName.trim());
        }
        sql += " ORDER BY role_name, permission_code";
        return jdbcTemplate.queryForList(sql, p);
    }

    public List<Map<String, Object>> listMatrix() {
        return jdbcTemplate.queryForList(
                "SELECT action_code, action_name, allowed_roles, access_mode, maker_checker_flag, "
                        + "audit_level, environment_restriction FROM admin_access_matrix ORDER BY action_code",
                new MapSqlParameterSource());
    }
}
