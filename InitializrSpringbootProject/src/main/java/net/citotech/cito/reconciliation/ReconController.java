package net.citotech.cito.reconciliation;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation")
public class ReconController {
    private final ReconService service;

    public ReconController(ReconService service) {
        this.service = service;
    }

    @PostMapping(path = "/import")
    public long importStatement(@RequestParam("provider") String provider,
                                @RequestParam(value = "fileName", defaultValue = "statement.csv") String fileName,
                                @RequestParam(value = "importedBy", defaultValue = "system") String importedBy,
                                @RequestBody String csvText) {
        return service.importStatement(provider, fileName, importedBy, csvText);
    }

    @PostMapping(path = "/auto-match")
    public int autoMatch() {
        return service.autoMatch();
    }

    @GetMapping(path = "/unmatched")
    public List<ReconciliationRecord> unmatched(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.unmatched(limit);
    }

    @PostMapping(path = "/manual-match")
    public String manualMatch(@RequestParam("recordId") long recordId,
                              @RequestParam("transactionId") String transactionId,
                              @RequestParam(value = "reason", required = false) String reason) {
        service.approveMatch(recordId, transactionId, reason);
        return "updated";
    }
}
