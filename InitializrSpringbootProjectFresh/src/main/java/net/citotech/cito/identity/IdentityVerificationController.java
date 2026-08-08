package net.citotech.cito.identity;

import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity-verification surface (S5 pilot, gated by the {@code identity-gnugrid} feature flag).
 *
 * <p>Admin endpoints let an operator (KYB reviewer) record consent, run a NIN verification, and
 * look up the masked result. The {@code /provider/gnugrid/callback} endpoint is the provider's
 * asynchronous delivery path and is intentionally NOT admin-scoped - it is validated by the
 * connector's header check (same trust boundary as channel webhooks).
 */
@RestController
public class IdentityVerificationController {

    private final IdentityVerificationService verificationService;
    private final List<IdentityVerificationConnector> connectors;

    public IdentityVerificationController(
            IdentityVerificationService verificationService,
            List<IdentityVerificationConnector> connectors) {
        this.verificationService = verificationService;
        this.connectors = connectors;
    }

    @PostMapping("/api/v2/admin/identity/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verify(
            @RequestParam("merchantId") long merchantId,
            @RequestParam("nin") String nin,
            @RequestParam(value = "fullName", required = false) String fullName,
            @RequestParam(value = "msisdn", required = false) String msisdn,
            @RequestParam(value = "consentGranted", defaultValue = "false") boolean consentGranted,
            @RequestParam(value = "requestedBy", required = false) String requestedBy) {
        try {
            Map<String, Object> result =
                    verificationService.verify(
                            merchantId, nin, fullName, msisdn, consentGranted, requestedBy);
            return ResponseEntity.ok(result);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "IDENTITY_VERIFICATION_REJECTED",
                                    "message",
                                    e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_IDENTITY_REQUEST", "message", e.getMessage()));
        }
    }

    @GetMapping("/api/v2/admin/identity/requests")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> listRequests(@RequestParam("merchantId") long merchantId) {
        if (merchantId <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_MERCHANT", "message", "merchantId is required."));
        }
        return ResponseEntity.ok(verificationService.listRequests(merchantId));
    }

    @GetMapping("/api/v2/admin/identity/requests/{reference}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> requestByReference(@PathVariable("reference") String reference) {
        Map<String, Object> row = verificationService.findRequestByReference(reference);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(row);
    }

    @GetMapping("/api/v2/admin/identity/verified")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> verifiedByNin(@RequestParam("nin") String nin) {
        Map<String, Object> row = verificationService.findVerifiedByNin(nin);
        if (row == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(row);
    }

    @PostMapping("/api/v2/identity/provider/gnugrid/callback")
    public ResponseEntity<?> gnugridCallback(
            @RequestBody String body,
            @RequestHeader(value = "X-Gnugrid-Signature", defaultValue = "") String signature) {
        IdentityVerificationConnector connector =
                connectors.stream()
                        .filter(
                                c ->
                                        GnuGridConnector.PROVIDER_CODE.equalsIgnoreCase(
                                                c.providerCode()))
                        .findFirst()
                        .orElse(null);
        if (connector == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(
                            Map.of(
                                    "code",
                                    "PROVIDER_NOT_CONFIGURED",
                                    "message",
                                    "gnugrid is not configured."));
        }
        if (!connector.validateCallbackHeaders(Map.of("X-Gnugrid-Signature", signature))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    "code",
                                    "INVALID_CALLBACK_SIGNATURE",
                                    "message",
                                    "Callback signature failed validation."));
        }
        try {
            IdentityRecords.VerifiedIdentity result = connector.parseCallback(body, Map.of());
            return ResponseEntity.ok(
                    Map.of("received", true, "status", result.verificationStatus()));
        } catch (IdentityVerificationException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_CALLBACK", "message", e.getMessage()));
        }
    }
}
