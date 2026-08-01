package net.citotech.cito.audit;

import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only, on-demand audit-trail integrity check (audit F8). */
@RestController
@RequestMapping(path = "/api/v2/admin/audit-trail")
@PreAuthorize("hasRole('ADMIN')")
public class AuditChainVerificationController {
    private final AuditChainVerificationService verificationService;

    public AuditChainVerificationController(AuditChainVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    @GetMapping(path = "/verify")
    public Map<String, Object> verify() {
        AuditChainVerificationService.Result adminTrail = verificationService.verifyAuditTrail();
        AuditChainVerificationService.Result merchantTrail = verificationService.verifyMerchantAuditTrail();
        return Map.of(
            "intact", adminTrail.intact() && merchantTrail.intact(),
            "auditTrail", resultAsMap(adminTrail),
            "merchantAuditTrail", resultAsMap(merchantTrail));
    }

    private Map<String, Object> resultAsMap(AuditChainVerificationService.Result result) {
        return Map.of(
            "intact", result.intact(),
            "hashedRows", result.hashedRows(),
            "brokenAtIds", result.brokenAtIds());
    }
}
