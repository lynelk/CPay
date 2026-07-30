package net.citotech.cito.reconciliation;

import net.citotech.cito.gateway.PaymentGatewayException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

// Audit E3 (extended by O2): method-level reinforcement of the /api/v2/admin/** -> hasRole("ADMIN")
// rule already enforced by SecurityConfig's filterChain (defense in depth, not a replacement for
// it). Statement import/auto-match/manual-match are equally sensitive admin actions as the
// review/settlement/finance controllers already hardened this way, so bring this one in line too.
@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation")
@PreAuthorize("hasRole('ADMIN')")
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

    /**
     * Audit O2: purpose-built candidate search for the manual-match workbench's "find a CPay
     * transaction to match against" panel. Deliberately not reusing the legacy {@code
     * TransactionsLogController#getTransactions} endpoint - that one is session-auth /
     * JSON-string-body and returns the full transaction-log shape, whereas this is a narrow,
     * already-admin-gated REST lookup by reference/amount/date only. At least one of reference,
     * amount, from, or to must be supplied - otherwise this returns no rows rather than scanning
     * the whole transaction log.
     */
    @GetMapping(path = "/candidate-transactions")
    public List<CandidateTransaction> candidateTransactions(
            @RequestParam(value = "reference", required = false) String reference,
            @RequestParam(value = "amount", required = false) String amount,
            @RequestParam(value = "currency", required = false) String currency,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "limit", defaultValue = "25") int limit) {
        BigDecimal amountValue =
                (amount == null || amount.isBlank()) ? null : new BigDecimal(amount);
        LocalDate fromDate = (from == null || from.isBlank()) ? null : LocalDate.parse(from);
        LocalDate toDate = (to == null || to.isBlank()) ? null : LocalDate.parse(to);
        return service.candidateTransactions(
                reference, amountValue, currency, fromDate, toDate, limit);
    }
}
