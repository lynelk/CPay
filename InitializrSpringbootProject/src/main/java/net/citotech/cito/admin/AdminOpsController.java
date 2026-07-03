package net.citotech.cito.admin;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/permissions")
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
