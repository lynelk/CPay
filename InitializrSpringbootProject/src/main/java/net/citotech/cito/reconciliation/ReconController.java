package net.citotech.cito.reconciliation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation")
public class ReconController {
    private final ReconService reconService;

    public ReconController(ReconService reconService) {
        this.reconService = reconService;
    }

    @PostMapping(path = "/auto-match")
    public ResponseEntity<Map<String, Object>> autoMatch() {
        Map<String, Object> response = new HashMap<>();
        response.put("matched", reconService.autoMatch());
        return ResponseEntity.ok(response);
    }

    @GetMapping(path = "/unmatched")
    public List<ReconciliationRecord> unmatched(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return reconService.unmatched(limit);
    }

    @PostMapping(path = "/manual-match")
    public ResponseEntity<Map<String, Object>> manualMatch(@RequestParam("recordId") long recordId,
                                                           @RequestParam("transactionId") String transactionId,
                                                           @RequestParam(value = "reason", required = false) String reason) {
        reconService.approveMatch(recordId, transactionId, reason);
        Map<String, Object> response = new HashMap<>();
        response.put("status", "updated");
        return ResponseEntity.ok(response);
    }
}
