package net.citotech.cito.treasury;

import java.math.BigDecimal;
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

/**
 * Controlled provider-float administration. There is intentionally no "set balance" endpoint.
 * Credits, debits and rebalances are maker-checker requests that post only after approval.
 */
@RestController
@RequestMapping("/api/v2/admin/provider-treasury")
@PreAuthorize("hasRole('ADMIN')")
public class ProviderTreasuryAdminController {
    private final ProviderTreasuryService service;

    public ProviderTreasuryAdminController(ProviderTreasuryService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public List<Map<String, Object>> accounts() {
        return service.listAccounts();
    }

    @GetMapping("/adjustments")
    public List<Map<String, Object>> adjustments() {
        return service.listAdjustments();
    }

    @PostMapping("/adjustments")
    public Map<String, Object> requestAdjustment(
            @RequestBody Map<String, Object> body, Authentication authentication) {
        return service.requestAdjustment(body, actor(authentication));
    }

    @PostMapping("/adjustments/{id}/approve")
    public Map<String, Object> approveAdjustment(
            @PathVariable long id, Authentication authentication) {
        return service.approveAdjustment(id, actor(authentication));
    }

    @PostMapping("/adjustments/{id}/reject")
    public Map<String, Object> rejectAdjustment(
            @PathVariable long id, Authentication authentication) {
        return service.rejectAdjustment(id, actor(authentication));
    }

    @GetMapping("/reservations")
    public List<Map<String, Object>> reservations() {
        return service.listReservations();
    }

    @PostMapping("/reservations/{id}/resolve")
    public Map<String, Object> resolveReservation(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        boolean success =
                Boolean.TRUE.equals(body.get("success"))
                        || "true".equalsIgnoreCase(String.valueOf(body.get("success")));
        return service.resolvePending(
                id,
                success,
                String.valueOf(body.getOrDefault("providerReference", "")),
                actor(authentication));
    }

    @GetMapping("/exposures")
    public List<Map<String, Object>> exposures() {
        return service.listExposures();
    }

    @GetMapping("/journal")
    public List<Map<String, Object>> journal() {
        return service.listJournal();
    }

    @GetMapping("/reconciliations")
    public List<Map<String, Object>> reconciliations() {
        return service.listReconciliations();
    }

    @PostMapping("/accounts/{id}/reconcile")
    public Map<String, Object> reconcile(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        return service.reconcile(id, body, actor(authentication));
    }

    @PostMapping("/accounts/{id}/low-float-threshold")
    public Map<String, Object> lowFloatThreshold(
            @PathVariable long id, @RequestBody Map<String, Object> body) {
        Object raw = body.get("lowFloatThreshold");
        BigDecimal threshold = raw == null ? BigDecimal.ZERO : new BigDecimal(String.valueOf(raw));
        return service.setLowFloatThreshold(id, threshold);
    }

    private String actor(Authentication authentication) {
        return authentication == null ? "" : authentication.getName();
    }
}
