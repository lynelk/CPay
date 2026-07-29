package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it). This is
// the reconciliation approval/rejection workflow, so it is one of the clearest "sensitive admin
// action" candidates for this hardening.
@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation/reviews")
@PreAuthorize("hasRole('ADMIN')")
public class ReconciliationReviewController {
    private final ReconciliationReviewService service;

    public ReconciliationReviewController(ReconciliationReviewService service) {
        this.service = service;
    }

    @PostMapping(path = "/request")
    public String request(@RequestParam("recordId") long recordId,
                          @RequestParam(value = "transactionId", required = false) String transactionId,
                          @RequestParam("type") String type,
                          @RequestParam("amount") String amount,
                          @RequestParam("currency") String currency,
                          @RequestParam("reason") String reason,
                          @RequestParam("requestedBy") String requestedBy) {
        service.request(recordId, transactionId, type, new BigDecimal(amount), currency, reason, requestedBy);
        return "requested";
    }

    @GetMapping(path = "/pending")
    public List<ReconciliationReview> pending(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.pending(limit);
    }

    @PostMapping(path = "/approve")
    public String approve(@RequestParam("id") long id,
                          @RequestParam("reviewedBy") String reviewedBy,
                          @RequestParam(value = "note", required = false) String note) {
        service.approve(id, reviewedBy, note);
        return "approved";
    }

    @PostMapping(path = "/reject")
    public String reject(@RequestParam("id") long id,
                         @RequestParam("reviewedBy") String reviewedBy,
                         @RequestParam(value = "note", required = false) String note) {
        service.reject(id, reviewedBy, note);
        return "rejected";
    }
}

