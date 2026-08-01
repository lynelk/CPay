package net.citotech.cito.ledger;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/ledger")
@PreAuthorize("hasRole('ADMIN')")
public class LedgerAdminController {
    private final DoubleEntryLedgerService ledgerService;
    private final LedgerBalanceService ledgerBalanceService;

    public LedgerAdminController(
            DoubleEntryLedgerService ledgerService, LedgerBalanceService ledgerBalanceService) {
        this.ledgerService = ledgerService;
        this.ledgerBalanceService = ledgerBalanceService;
    }

    @PostMapping(path = "/trial-balance")
    public ResponseEntity<TrialBalanceResult> runTrialBalance(
            @RequestParam("currency") String currency,
            @RequestParam(value = "runDate", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate runDate) {
        LocalDate date = runDate == null ? LocalDate.now() : runDate;
        return ResponseEntity.ok(ledgerService.runTrialBalance(date, currency));
    }

    @PostMapping(path = "/balances/refresh")
    public ResponseEntity<Map<String, Object>> refreshBalances() {
        int rows = ledgerBalanceService.refreshBalances();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", "000");
        result.put("refreshedRows", rows);
        return ResponseEntity.ok(result);
    }

    @GetMapping(path = "/balances")
    public ResponseEntity<?> balances(
            @RequestParam(value = "ownerType", required = false) String ownerType,
            @RequestParam(value = "ownerId", required = false) Long ownerId,
            @RequestParam(value = "currency", required = false) String currency) {
        return ResponseEntity.ok(
                ledgerBalanceService.balancesForOwner(ownerType, ownerId, currency));
    }
}
