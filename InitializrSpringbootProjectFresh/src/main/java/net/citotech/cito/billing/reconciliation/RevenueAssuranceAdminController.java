package net.citotech.cito.billing.reconciliation;

import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/billing/revenue-assurance")
@PreAuthorize("hasRole('ADMIN')")
public class RevenueAssuranceAdminController {
    private final RevenueAssuranceService service;

    public RevenueAssuranceAdminController(RevenueAssuranceService service) {
        this.service = service;
    }

    @GetMapping("/tenants/{tenantId}/summary")
    public ResponseEntity<?> summary(@PathVariable long tenantId) {
        try {
            return ResponseEntity.ok(service.summarize(tenantId));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "BILLING_REVENUE_ASSURANCE_REJECTED",
                                    "message",
                                    e.getMessage()));
        }
    }
}
