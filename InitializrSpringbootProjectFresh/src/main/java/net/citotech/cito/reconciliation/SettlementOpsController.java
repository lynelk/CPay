package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it). This
// controller opens/closes settlement batches and runs settlement sweeps, so it is one of the
// clearest "sensitive admin action" candidates for this hardening.
//
// Maker-checker: POST /close is the maker submit (batch -> PENDING_APPROVAL); a different actor
// must POST /close/approve to reach CLOSED. POST /close/reject records the reason and keeps the
// batch pending so the maker can correct and re-submit.
@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation/settlements")
@PreAuthorize("hasRole('ADMIN')")
public class SettlementOpsController {
    private final SettlementOpsService service;
    private final SettlementScheduleService scheduleService;
    private final AdminAuditService auditService;

    public SettlementOpsController(
            SettlementOpsService service,
            SettlementScheduleService scheduleService,
            AdminAuditService auditService) {
        this.service = service;
        this.scheduleService = scheduleService;
        this.auditService = auditService;
    }

    @PostMapping(path = "/open")
    public String open(
            @RequestParam("reference") String reference,
            @RequestParam("provider") String provider,
            @RequestParam("channel") String channel,
            @RequestParam("currency") String currency,
            @RequestParam("expectedAmount") String expectedAmount,
            @RequestParam(value = "openedBy", defaultValue = "system") String openedBy) {
        auditService.record("SETTLEMENT_OPERATIONS", "SETTLEMENT_BATCH_OPEN", reference, openedBy);
        service.openBatch(
                reference, provider, channel, currency, new BigDecimal(expectedAmount), openedBy);
        return "opened";
    }

    @PostMapping(path = "/flag-record")
    public String flagRecord(
            @RequestParam("recordId") long recordId,
            @RequestParam("category") String category,
            @RequestParam("batchReference") String batchReference) {
        auditService.record(
                "RECONCILIATION_APPROVE",
                "SETTLEMENT_RECORD_FLAG",
                batchReference + ":" + recordId,
                "system");
        return "updated=" + service.flagRecord(recordId, category, batchReference);
    }

    /** Maker submit for batch close. */
    @PostMapping(path = "/close")
    public String close(
            @RequestParam("reference") String reference,
            @RequestParam(value = "closedBy", defaultValue = "system") String closedBy) {
        auditService.record(
                "RECONCILIATION_APPROVE", "SETTLEMENT_CLOSE_SUBMIT", reference, closedBy);
        return "closed=" + service.requestBatchClose(reference, closedBy);
    }

    /** Checker approval for a submitted batch close. */
    @PostMapping(path = "/close/approve")
    public ResponseEntity<?> approveClose(
            @RequestParam("reference") String reference,
            @RequestParam("approvedBy") String approvedBy) {
        int updated = service.approveBatchClose(reference, approvedBy);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Settlement batch close approval rejected: a different actor than the requester must approve, and the batch must be awaiting approval");
        }
        auditService.record(
                "RECONCILIATION_APPROVE", "SETTLEMENT_CLOSE_APPROVE", reference, approvedBy);
        return ResponseEntity.ok(
                java.util.Map.of("code", "000", "reference", reference, "status", "CLOSED"));
    }

    /** Checker rejection for a submitted batch close. */
    @PostMapping(path = "/close/reject")
    public ResponseEntity<?> rejectClose(
            @RequestParam("reference") String reference,
            @RequestParam("rejectedBy") String rejectedBy,
            @RequestParam(value = "reason", required = false) String reason) {
        int updated = service.rejectBatchClose(reference, rejectedBy, reason);
        if (updated == 0) {
            throw new PaymentGatewayException(
                    "Settlement batch close rejection failed: a different actor than the requester must reject, and the batch must be awaiting approval");
        }
        auditService.record(
                "RECONCILIATION_APPROVE", "SETTLEMENT_CLOSE_REJECT", reference, rejectedBy);
        return ResponseEntity.ok(
                java.util.Map.of(
                        "code", "000", "reference", reference, "status", "PENDING_APPROVAL"));
    }

    @PostMapping(path = "/schedule")
    public String schedule(
            @RequestParam("merchantId") long merchantId,
            @RequestParam("provider") String provider,
            @RequestParam("channel") String channel,
            @RequestParam("currency") String currency,
            @RequestParam(value = "minimumRetainedBalance", defaultValue = "0")
                    String minimumRetainedBalance,
            @RequestParam(value = "sweepHour", defaultValue = "2") int sweepHour) {
        scheduleService.configure(
                merchantId,
                provider,
                channel,
                currency,
                new BigDecimal(minimumRetainedBalance),
                sweepHour);
        return "scheduled";
    }

    @PostMapping(path = "/run-due")
    public List<SettlementSweepResult> runDue(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "hour", required = false) Integer hour) {
        LocalDate runDate =
                date == null || date.trim().isEmpty() ? LocalDate.now() : LocalDate.parse(date);
        int sweepHour = hour == null ? java.time.LocalTime.now().getHour() : hour;
        return scheduleService.runDueSweeps(runDate, sweepHour);
    }
}
