package net.citotech.cito.reconciliation;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/recon-finance")
public class ReconFinanceController {
    private final FinanceWorkflowService service;

    public ReconFinanceController(FinanceWorkflowService service) {
        this.service = service;
    }

    @PostMapping(path = "/post")
    public String post(@RequestParam("reviewId") long reviewId) {
        return "posted=" + service.postApprovedReview(reviewId, "system");
    }

    @PostMapping(path = "/close")
    public long close(@RequestParam("date") String date,
                      @RequestParam(value = "currency", defaultValue = "UGX") String currency) {
        return service.dailyClose(date, currency, "system");
    }

    @GetMapping(path = "/summary")
    public Map<String, Object> summary(@RequestParam(value = "currency", defaultValue = "UGX") String currency) {
        return service.report(currency);
    }
}
