package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/billing/baas")
@PreAuthorize("hasRole('ADMIN')")
public class BillingBaasAdminController {
    private final BillingBaasAdminService service;

    public BillingBaasAdminController(BillingBaasAdminService service) {
        this.service = service;
    }

    @GetMapping("/tenants/{tenantId}/profile")
    public ResponseEntity<?> profile(@PathVariable long tenantId) {
        try {
            return ResponseEntity.ok(service.tenantProfile(tenantId));
        } catch (PaymentGatewayException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/tenants/{tenantId}/profile/review")
    public ResponseEntity<?> reviewProfile(
            @PathVariable long tenantId,
            @RequestBody ProfileReviewRequest body,
            Principal principal) {
        try {
            return ResponseEntity.ok(
                    service.reviewTenant(
                            tenantId,
                            body.legalStatus(),
                            body.commercialStatus(),
                            body.taxStatus(),
                            body.fundsFlowStatus(),
                            actor(principal)));
        } catch (PaymentGatewayException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/tenants/{tenantId}/profile/activate")
    public ResponseEntity<?> activateProfile(@PathVariable long tenantId, Principal principal) {
        try {
            return ResponseEntity.ok(service.activateTenant(tenantId, actor(principal)));
        } catch (PaymentGatewayException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/tenants/{tenantId}/credentials")
    public ResponseEntity<?> provisionCredential(
            @PathVariable long tenantId, @RequestBody CredentialRequest body, Principal principal) {
        try {
            return ResponseEntity.ok(
                    service.provisionCredential(
                            tenantId,
                            body.projectReference(),
                            body.environment(),
                            body.displayName(),
                            body.scopes(),
                            body.requestsPerMinute(),
                            body.expiresAt(),
                            actor(principal)));
        } catch (PaymentGatewayException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/tenants/{tenantId}/accounts/{accountReference}/credit-limit")
    public ResponseEntity<?> setCreditLimit(
            @PathVariable long tenantId,
            @PathVariable String accountReference,
            @RequestBody CreditLimitRequest body,
            Principal principal) {
        try {
            return ResponseEntity.ok(
                    service.setCreditLimit(
                            tenantId, accountReference, body.creditLimit(), actor(principal)));
        } catch (PaymentGatewayException e) {
            return badRequest(e);
        }
    }

    @PostMapping("/tenants/{tenantId}/accounts/{accountReference}/top-ups")
    public ResponseEntity<?> topUp(
            @PathVariable long tenantId,
            @PathVariable String accountReference,
            @RequestBody TopUpRequest body,
            Principal principal) {
        try {
            return ResponseEntity.ok(
                    service.topUp(
                            tenantId,
                            accountReference,
                            body.amount(),
                            body.verifiedPaymentReference(),
                            actor(principal)));
        } catch (PaymentGatewayException e) {
            return badRequest(e);
        }
    }

    private String actor(Principal principal) {
        return principal == null || principal.getName() == null ? "system" : principal.getName();
    }

    private ResponseEntity<?> badRequest(PaymentGatewayException e) {
        return ResponseEntity.badRequest()
                .body(
                        Map.of(
                                "code",
                                "BILLING_BAAS_ADMIN_REJECTED",
                                "message",
                                e.getMessage() == null
                                        ? "BaaS admin request failed"
                                        : e.getMessage()));
    }

    public record ProfileReviewRequest(
            String legalStatus,
            String commercialStatus,
            String taxStatus,
            String fundsFlowStatus) {}

    public record CredentialRequest(
            String projectReference,
            String environment,
            String displayName,
            List<String> scopes,
            Integer requestsPerMinute,
            Instant expiresAt) {}

    public record CreditLimitRequest(BigDecimal creditLimit) {}

    public record TopUpRequest(BigDecimal amount, String verifiedPaymentReference) {}
}
