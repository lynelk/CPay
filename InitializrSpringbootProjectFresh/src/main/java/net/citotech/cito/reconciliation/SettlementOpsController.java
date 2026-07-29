package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Audit E3: method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN") rule already
// enforced by SecurityConfig's filterChain (defense in depth, not a replacement for it). This
// controller opens/closes settlement batches and runs settlement sweeps, so it is one of the
// clearest "sensitive admin action" candidates for this hardening.
@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation/settlements")
@PreAuthorize("hasRole('ADMIN')")
public class SettlementOpsController {
    private final SettlementOpsService service;
    private final SettlementScheduleService scheduleService;

    public SettlementOpsController(SettlementOpsService service, SettlementScheduleService scheduleService) {
        this.service = service;
        this.scheduleService = scheduleService;
    }

    @PostMapping(path = "/open")
    public String open(@RequestParam("reference") String reference,
                       @RequestParam("provider") String provider,
                       @RequestParam("channel") String channel,
                       @RequestParam("currency") String currency,
                       @RequestParam("expectedAmount") String expectedAmount,
                       @RequestParam(value = "openedBy", defaultValue = "system") String openedBy) {
        service.openBatch(reference, provider, channel, currency, new BigDecimal(expectedAmount), openedBy);
        return "opened";
    }

    @PostMapping(path = "/flag-record")
    public String flagRecord(@RequestParam("recordId") long recordId,
                             @RequestParam("category") String category,
                             @RequestParam("batchReference") String batchReference) {
        return "updated=" + service.flagRecord(recordId, category, batchReference);
    }

    @PostMapping(path = "/close")
    public String close(@RequestParam("reference") String reference,
                        @RequestParam(value = "closedBy", defaultValue = "system") String closedBy) {
        return "closed=" + service.closeBatch(reference, closedBy);
    }

    @PostMapping(path = "/schedule")
    public String schedule(@RequestParam("merchantId") long merchantId,
                           @RequestParam("provider") String provider,
                           @RequestParam("channel") String channel,
                           @RequestParam("currency") String currency,
                           @RequestParam(value = "minimumRetainedBalance", defaultValue = "0") String minimumRetainedBalance,
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
    public List<SettlementSweepResult> runDue(@RequestParam(value = "date", required = false) String date,
                                              @RequestParam(value = "hour", required = false) Integer hour) {
        LocalDate runDate = date == null || date.trim().isEmpty() ? LocalDate.now() : LocalDate.parse(date);
        int sweepHour = hour == null ? java.time.LocalTime.now().getHour() : hour;
        return scheduleService.runDueSweeps(runDate, sweepHour);
    }
}

