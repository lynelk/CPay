package net.citotech.cito.admin;

import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it).
@RestController
@RequestMapping(path = "/api/v2/admin/feature-flags")
@PreAuthorize("hasRole('ADMIN')")
public class FeatureFlagController {
    private final FeatureFlagService featureFlags;

    public FeatureFlagController(FeatureFlagService featureFlags) {
        this.featureFlags = featureFlags;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return featureFlags.list();
    }

    @GetMapping(path = "/{flagKey}")
    public Map<String, Object> status(@PathVariable String flagKey) {
        return Map.of("flagKey", flagKey, "enabled", featureFlags.isEnabled(flagKey));
    }

    @PostMapping(path = "/{flagKey}")
    public Map<String, Object> save(
            @PathVariable String flagKey,
            @RequestBody Map<String, Object> request) {
        boolean enabled = Boolean.TRUE.equals(request.get("enabled"));
        String description = String.valueOf(request.getOrDefault("description", ""));
        return featureFlags.save(flagKey, enabled, description);
    }
}
