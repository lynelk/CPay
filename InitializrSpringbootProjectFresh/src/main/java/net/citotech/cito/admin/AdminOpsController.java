package net.citotech.cito.admin;

import java.util.Map;
import net.citotech.cito.merchant.MerchantKeyReencryptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it).
@RestController
@RequestMapping(path = "/api/v2/admin/permissions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOpsController {
    private final AdminPermissionService permissions;
    private final MerchantKeyReencryptionService keyReencryptionService;

    public AdminOpsController(AdminPermissionService permissions,
                              MerchantKeyReencryptionService keyReencryptionService) {
        this.permissions = permissions;
        this.keyReencryptionService = keyReencryptionService;
    }

    @PostMapping(path = "/seed-defaults")
    public String seedDefaults() {
        permissions.require("ADMIN_PERMISSION_MANAGE", "seed-default-permissions", "admin_permissions");
        permissions.seedDefaultPermissions();
        return "seeded";
    }

    /** Audit E6: on-demand re-encryption of a merchant's RSA private key under the dedicated key. */
    @PostMapping(path = "/merchant-key-reencrypt")
    public Map<String, Object> reencryptMerchantKey(
            @RequestParam("merchantId") long merchantId) {
        boolean upgraded = keyReencryptionService.upgradeMerchant(merchantId);
        return Map.of("code", "000", "upgraded", upgraded);
    }
}
