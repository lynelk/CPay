package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/reconciliation/reviews")
public class ReconciliationReviewController {
    private final ReconciliationReviewService service;

    public ReconciliationReviewController(ReconciliationReviewService service) {
        this.service = service;
    }

    @PostMapping(path = "/request")
    public String request(@RequestParam("recordId") long recordId,
                          @RequestParam(value = "transactionId", required = false) String transactionId,
                          @RequestParam("type") String type,
                          @RequestParam("amount") String amount,
                          @RequestParam("currency") String currency,
                          @RequestParam("reason") String reason,
                          @RequestParam("requestedBy") String requestedBy) {
        service.request(recordId, transactionId, type, new BigDecimal(amount), currency, reason, requestedBy);
        return "requested";
    }

    @GetMapping(path = "/pending")
    public List<ReconciliationReview> pending(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        return service.pending(limit);
    }

    @PostMapping(path = "/approve")
    public String approve(@RequestParam("id") long id,
                          @RequestParam("reviewedBy") String reviewedBy,
                          @RequestParam(value = "note", required = false) String note) {
        service.approve(id, reviewedBy, note);
        return "approved";
    }

    @PostMapping(path = "/reject")
    public String reject(@RequestParam("id") long id,
                         @RequestParam("reviewedBy") String reviewedBy,
                         @RequestParam(value = "note", required = false) String note) {
        service.reject(id, reviewedBy, note);
        return "rejected";
    }
}
