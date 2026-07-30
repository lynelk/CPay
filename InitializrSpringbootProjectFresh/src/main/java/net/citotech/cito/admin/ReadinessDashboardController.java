package net.citotech.cito.admin;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it).
@RestController
@RequestMapping(path = "/api/v2/admin/readiness")
@PreAuthorize("hasRole('ADMIN')")
public class ReadinessDashboardController {
    private final ReadinessDashboardService service;

    public ReadinessDashboardController(ReadinessDashboardService service) {
        this.service = service;
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary() {
        return service.summary();
    }

    @GetMapping(path = "/merchants/{merchantId}")
    public Map<String, Object> merchantSummary(@PathVariable("merchantId") long merchantId) {
        return service.merchantSummary(merchantId);
    }
}

