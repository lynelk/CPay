package net.citotech.cito.reconciliation;

import java.io.IOException;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation")
public class ReconController {
    private final ReconService service;

    public ReconController(ReconService service) {
        this.service = service;
    }

    /**
     * Audit O1: accepts either a CSV or an XLSX statement file (dispatched by file extension - see
     * {@link AbstractTabularStatementParser}); previously this only accepted a raw CSV text body.
     */
    @PostMapping(path = "/import")
    public long importStatement(
            @RequestParam("provider") String provider,
            @RequestParam(value = "importedBy", defaultValue = "system") String importedBy,
            @RequestPart("file") MultipartFile file) {
        try {
            return service.importStatement(
                    provider, file.getOriginalFilename(), importedBy, file.getBytes());
        } catch (IOException e) {
            throw new PaymentGatewayException(
                    "Unable to read uploaded statement file: " + e.getMessage());
        }
    }

    @PostMapping(path = "/auto-match")
    public int autoMatch() {
        return service.autoMatch();
    }

    @GetMapping(path = "/unmatched")
    public List<ReconciliationRecord> unmatched(
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.unmatched(limit);
    }

    @PostMapping(path = "/manual-match")
    public String manualMatch(
            @RequestParam("recordId") long recordId,
            @RequestParam("transactionId") String transactionId,
            @RequestParam(value = "reason", required = false) String reason) {
        service.approveMatch(recordId, transactionId, reason);
        return "updated";
    }
}
