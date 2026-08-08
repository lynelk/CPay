package net.citotech.cito.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Feature-registry admin surface (ADR 0002): the global {@code feature_flags} catalog plus the
 * per-merchant overrides in {@code merchant_feature_flags} (V36). Operators use this to roll a
 * feature out or back per merchant without a deploy; the effective view shows, for each feature,
 * the resolved on/off state for a merchant and whether a merchant override is present.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/feature-registry")
@PreAuthorize("hasRole('ADMIN')")
public class FeatureRegistryController {
    private final FeatureRegistryService registry;

    public FeatureRegistryController(FeatureRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping(path = "/features")
    public List<Map<String, Object>> globalFeatures() {
        return registry.listGlobal();
    }

    @GetMapping(path = "/merchants/{merchantId}")
    public List<Map<String, Object>> effectiveForMerchant(
            @PathVariable("merchantId") long merchantId) {
        return registry.listEffective(merchantId);
    }

    @PostMapping(path = "/merchants/{merchantId}/features/{flagKey}")
    public ResponseEntity<?> setMerchantOverride(
            @PathVariable("merchantId") long merchantId,
            @PathVariable("flagKey") String flagKey,
            @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
            String description = String.valueOf(body.getOrDefault("description", ""));
            return ResponseEntity.ok(
                    registry.setMerchantOverride(merchantId, flagKey, enabled, description));
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("code", "INVALID_FLAG_KEY");
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping(path = "/merchants/{merchantId}/features/{flagKey}")
    public Map<String, Object> removeMerchantOverride(
            @PathVariable("merchantId") long merchantId,
            @PathVariable("flagKey") String flagKey,
            @RequestParam(name = "removedBy", required = false) String removedBy) {
        int updated = registry.removeMerchantOverride(merchantId, flagKey);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("merchantId", merchantId);
        result.put("flagKey", flagKey);
        result.put("removed", updated);
        result.put("removedBy", removedBy == null || removedBy.isBlank() ? "system" : removedBy);
        return result;
    }
}
