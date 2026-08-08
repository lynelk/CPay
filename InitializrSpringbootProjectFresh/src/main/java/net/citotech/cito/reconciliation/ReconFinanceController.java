package net.citotech.cito.reconciliation;

import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
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
//
// Maker-checker: POST /close is the maker submit (row -> PENDING_APPROVAL); a different actor must
// POST /close/approve to reach CLOSED. POST /close/reject records the reason and keeps the row
// pending so the maker can correct and re-submit.
@RestController
@RequestMapping(path = "/api/v2/admin/recon-finance")
@PreAuthorize("hasRole('ADMIN')")
public class ReconFinanceController {
    private final FinanceWorkflowService service;
    private final AdminAuditService auditService;

    public ReconFinanceController(FinanceWorkflowService service, AdminAuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping(path = "/post")
    public String post(
            @RequestParam("reviewId") long reviewId,
            @RequestParam(value = "postedBy", defaultValue = "system") String postedBy) {
        auditService.record(
                "RECONCILIATION_APPROVE", "REVIEW_POST", "reviewId:" + reviewId, postedBy);
        return "posted=" + service.postApprovedReview(reviewId, postedBy);
    }

    /** Maker submit for daily close. Returns the close row id. */
    @PostMapping(path = "/close")
    public long close(
            @RequestParam("date") String date,
            @RequestParam(value = "currency", defaultValue = "UGX") String currency,
            @RequestParam(value = "submittedBy", defaultValue = "system") String submittedBy) {
        auditService.record(
                "RECONCILIATION_APPROVE", "DAILY_CLOSE_SUBMIT", date + ":" + currency, submittedBy);
        return service.submitDailyClose(date, currency, submittedBy);
    }

    /**
     * Checker approval for a submitted daily close. Fails with a business error when the approver
     * is the maker or the close is not pending.
     */
    @PostMapping(path = "/close/approve")
    public ResponseEntity<?> approveClose(
            @RequestParam("date") String date,
            @RequestParam(value = "currency", defaultValue = "UGX") String currency,
            @RequestParam("approvedBy") String approvedBy) {
        int updated = service.approveDailyClose(date, currency, approvedBy);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Daily close approval rejected: a different actor than the requester must approve, and the close must be awaiting approval");
        }
        auditService.record(
                "RECONCILIATION_APPROVE", "DAILY_CLOSE_APPROVE", date + ":" + currency, approvedBy);
        return ResponseEntity.ok(
                Map.of("code", "000", "closeDate", date, "currency", currency, "status", "CLOSED"));
    }

    /** Checker rejection for a submitted daily close. */
    @PostMapping(path = "/close/reject")
    public ResponseEntity<?> rejectClose(
            @RequestParam("date") String date,
            @RequestParam(value = "currency", defaultValue = "UGX") String currency,
            @RequestParam("rejectedBy") String rejectedBy,
            @RequestParam(value = "reason", required = false) String reason) {
        int updated = service.rejectDailyClose(date, currency, rejectedBy, reason);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Daily close rejection failed: a different actor than the requester must reject, and the close must be awaiting approval");
        }
        auditService.record(
                "RECONCILIATION_APPROVE", "DAILY_CLOSE_REJECT", date + ":" + currency, rejectedBy);
        return ResponseEntity.ok(
                Map.of(
                        "code",
                        "000",
                        "closeDate",
                        date,
                        "currency",
                        currency,
                        "status",
                        "PENDING_APPROVAL"));
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary(
            @RequestParam(value = "currency", defaultValue = "UGX") String currency) {
        return service.dashboard(currency);
    }

    /**
     * Daily-close history with maker/checker/rejection fields, most recent first (audit item: no
     * historical view existed - the summary endpoint never returned closed days).
     */
    @GetMapping(path = "/history")
    public List<Map<String, Object>> history(
            @RequestParam(value = "currency", defaultValue = "UGX") String currency,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.history(currency, limit);
    }
}
