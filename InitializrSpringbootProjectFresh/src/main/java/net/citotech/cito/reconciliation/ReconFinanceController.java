package net.citotech.cito.reconciliation;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it). This is
// the finance approval-posting surface (posts approved reconciliation reviews, runs daily close),
// so it is one of the clearest "sensitive admin action" candidates for this hardening.
@RestController
@RequestMapping(path = "/api/v2/admin/recon-finance")
@PreAuthorize("hasRole('ADMIN')")
public class ReconFinanceController {
    private final FinanceWorkflowService service;

    public ReconFinanceController(FinanceWorkflowService service) {
        this.service = service;
    }

    @PostMapping(path = "/post")
    public String post(@RequestParam("reviewId") long reviewId) {
        return "posted=" + service.postApprovedReview(reviewId, "system");
    }

    @PostMapping(path = "/close")
    public long close(@RequestParam("date") String date,
                      @RequestParam(value = "currency", defaultValue = "UGX") String currency) {
        return service.dailyClose(date, currency, "system");
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary(@RequestParam(value = "currency", defaultValue = "UGX") String currency) {
        return service.report(currency);
    }
}

