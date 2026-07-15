package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation/settlements")
public class SettlementOpsController {
    private final SettlementOpsService service;

    public SettlementOpsController(SettlementOpsService service) {
        this.service = service;
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
}

