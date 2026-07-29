package net.citotech.cito.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it).
@RestController
@RequestMapping(path = "/api/v2/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpsController {
    private final AdminPermissionService permissions;

    public AdminOpsController(AdminPermissionService permissions) {
        this.permissions = permissions;
    }

    @PostMapping(path = "/seed-defaults")
    public String seedDefaults() {
        permissions.require("ADMIN_PERMISSION_MANAGE", "seed-default-permissions", "admin_permissions");
        permissions.seedDefaultPermissions();
        return "seeded";
    }
}

