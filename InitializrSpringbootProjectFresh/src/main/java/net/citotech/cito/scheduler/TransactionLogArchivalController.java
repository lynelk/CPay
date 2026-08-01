package net.citotech.cito.scheduler;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual, admin-only trigger for a single transaction-log archival batch (audit F3), for ops
 * runbook use ahead of (or instead of) waiting on the scheduled sweep. Purge is intentionally not
 * exposed here - physically deleting rows is a separate, higher-risk decision left to the
 * dedicated purge-enabled scheduler flag rather than an on-demand admin call.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/archival/transactions-log")
@PreAuthorize("hasRole('ADMIN')")
public class TransactionLogArchivalController {
    private final TransactionLogArchivalService archivalService;

    public TransactionLogArchivalController(TransactionLogArchivalService archivalService) {
        this.archivalService = archivalService;
    }

    @PostMapping(path = "/run")
    public Map<String, Object> run(@RequestParam(value = "retentionDays", defaultValue = "365") int retentionDays,
                                   @RequestParam(value = "batchSize", defaultValue = "500") int batchSize) {
        int archived = archivalService.archiveBatch(retentionDays, batchSize);
        return Map.of("code", "000", "archived", archived);
    }
}
