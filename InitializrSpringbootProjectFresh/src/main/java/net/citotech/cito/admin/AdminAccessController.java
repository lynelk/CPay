package net.citotech.cito.admin;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * P0 §2: read surface for the admin RBAC catalog. Lists the role catalog, the permission grants
 * and the authorization matrix. Every read is permission-gated (ACCESS_CONTROL_READ) and class
 * annotated with the {@code hasRole('ADMIN')} reinforcement used across the v2 admin surface.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/access")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccessController {
    private final AdminAccessService accessService;
    private final AdminPermissionService permissions;

    public AdminAccessController(AdminAccessService accessService, AdminPermissionService permissions) {
        this.accessService = accessService;
        this.permissions = permissions;
    }

    @GetMapping(path = "/roles")
    public List<Map<String, Object>> roles() {
        permissions.require("ACCESS_CONTROL_READ", "access-roles-list", "admin_roles");
        return accessService.listRoles();
    }

    @GetMapping(path = "/permissions")
    public List<Map<String, Object>> permissions(
            @RequestParam(name = "role", required = false) String role) {
        permissions.require("ACCESS_CONTROL_READ", "access-permissions-list", "admin_permissions");
        return accessService.listPermissions(role);
    }

    @GetMapping(path = "/matrix")
    public List<Map<String, Object>> matrix() {
        permissions.require("ACCESS_CONTROL_READ", "access-matrix-list", "admin_access_matrix");
        return accessService.listMatrix();
    }
}
