package net.citotech.cito.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only ops dashboard over stuck/failed merchant callback and webhook deliveries.
 * Guarded by the {@code /api/v2/admin/**} -&gt; {@code hasRole("ADMIN")} rule in
 * {@link net.citotech.cito.config.SecurityConfig}, reinforced (audit E3) by the class-level
 * {@code @PreAuthorize} below - additive defense-in-depth, not a replacement for the path rule.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/delivery-ops")
@PreAuthorize("hasRole('ADMIN')")
public class DeliveryOpsDashboardController {
    private final DeliveryOpsDashboardService service;

    public DeliveryOpsDashboardController(DeliveryOpsDashboardService service) {
        this.service = service;
    }

    @GetMapping(path = "/summary")
    public DeliveryOpsDashboardService.DeliveryOpsSummary summary(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.summary(limit);
    }
}
