package net.citotech.cito.sharedprovider;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Administrative surface for CPay-sponsored provider credentials and merchant entitlements. */
@RestController
@RequestMapping("/api/v2/admin/shared-provider")
@PreAuthorize("hasRole('ADMIN')")
public class SharedProviderAdminController {
    private final SharedProviderAccessService service;

    public SharedProviderAdminController(SharedProviderAccessService service) {
        this.service = service;
    }

    @GetMapping("/entitlements")
    public List<Map<String, Object>> entitlements() {
        return service.listEntitlements();
    }

    @PostMapping("/entitlements")
    public Map<String, Object> requestEntitlement(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return service.requestEntitlement(body, actor(authentication));
    }

    @PostMapping("/entitlements/{id}/approve")
    public Map<String, Object> approveEntitlement(
            @PathVariable long id, Authentication authentication) {
        return service.approveEntitlement(id, actor(authentication));
    }

    @PostMapping("/entitlements/{id}/reject")
    public Map<String, Object> rejectEntitlement(
            @PathVariable long id, Authentication authentication) {
        return service.rejectEntitlement(id, actor(authentication));
    }

    @PostMapping("/entitlements/{id}/disable")
    public Map<String, Object> disableEntitlement(
            @PathVariable long id, Authentication authentication) {
        return service.disableEntitlement(id, actor(authentication));
    }

    @GetMapping("/credentials")
    public List<Map<String, Object>> credentials() {
        return service.listPlatformCredentials();
    }

    @PostMapping("/credentials")
    public Map<String, Object> saveCredential(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return service.savePlatformCredential(body, actor(authentication));
    }

    @PostMapping("/credentials/{id}/approve")
    public Map<String, Object> approveCredential(
            @PathVariable long id, Authentication authentication) {
        return service.approvePlatformCredential(id, actor(authentication));
    }

    @PostMapping("/credentials/{id}/disable")
    public Map<String, Object> disableCredential(
            @PathVariable long id, Authentication authentication) {
        return service.disablePlatformCredential(id, actor(authentication));
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }
}
