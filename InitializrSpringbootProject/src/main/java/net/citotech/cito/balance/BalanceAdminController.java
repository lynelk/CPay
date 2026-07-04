package net.citotech.cito.balance;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/balances")
public class BalanceAdminController {
    private final AuthoritativeBalanceService balanceService;

    public BalanceAdminController(AuthoritativeBalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @PostMapping(path = "/sync-legacy")
    public String syncLegacy(@RequestParam(value = "startedBy", defaultValue = "system") String startedBy) {
        int written = balanceService.backfillFromLegacy(startedBy);
        return "balances_written=" + written;
    }
}
