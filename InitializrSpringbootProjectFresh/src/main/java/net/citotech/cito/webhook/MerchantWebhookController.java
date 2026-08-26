package net.citotech.cito.webhook;

import java.util.Map;
import java.util.UUID;
import net.citotech.cito.admin.AdminAuditService;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/webhooks")
@PreAuthorize("hasRole('ADMIN')")
public class MerchantWebhookController {
    private final MerchantWebhookService webhookService;
    private final AdminAuditService auditService;

    public MerchantWebhookController(
            MerchantWebhookService webhookService, AdminAuditService auditService) {
        this.webhookService = webhookService;
        this.auditService = auditService;
    }

    @PostMapping(path = "/merchants/{merchantId}")
    public ResponseEntity<?> register(
            @PathVariable("merchantId") long merchantId, @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(
                            webhookService.registerEndpoint(
                                    merchantId,
                                    body.get("eventType"),
                                    body.get("endpointUrl"),
                                    body.get("actor")));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "WEBHOOK_REJECTED", e.getMessage());
        }
    }

    @GetMapping(path = "/merchants/{merchantId}")
    public ResponseEntity<?> list(@PathVariable("merchantId") long merchantId) {
        return ResponseEntity.ok(webhookService.listEndpoints(merchantId));
    }

    @PostMapping(path = "/endpoints/{endpointId}/rotate-secret")
    public ResponseEntity<?> rotate(@PathVariable("endpointId") long endpointId) {
        try {
            return ResponseEntity.ok(webhookService.rotateSecret(endpointId));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "WEBHOOK_NOT_FOUND", e.getMessage());
        }
    }

    /**
     * Merchant callback verification (audit item: merchants had a delivery log and replay but no
     * way to verify their callback URL before going live). Queues a synthetic event for the
     * merchant's active endpoint(s) of the requested type so the delivery can be observed in the
     * log end to end. Audited.
     */
    @PostMapping(path = "/merchants/{merchantId}/test-callback")
    public ResponseEntity<?> testCallback(
            @PathVariable("merchantId") long merchantId,
            @RequestParam(value = "eventType", defaultValue = "payment.completed") String eventType,
            @RequestParam(value = "actor", defaultValue = "system") String actor) {
        try {
            int queued = webhookService.testCallback(merchantId, eventType);
            auditService.record(
                    "WEBHOOK_OPERATIONS",
                    "WEBHOOK_TEST_CALLBACK",
                    "merchant:" + merchantId + ":" + eventType,
                    actor);
            return ResponseEntity.ok(
                    Map.of(
                            "code", "000",
                            "merchantId", merchantId,
                            "eventType", eventType,
                            "queued", queued,
                            "message",
                                    queued > 0
                                            ? "Test event queued - watch the delivery log for this eventType"
                                            : "No active endpoint for this eventType - register one first"));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "WEBHOOK_REJECTED", e.getMessage());
        }
    }

    @PostMapping(path = "/deliveries/{deliveryId}/replay")
    public ResponseEntity<?> replay(
            @PathVariable("deliveryId") long deliveryId,
            @RequestParam(value = "actor", defaultValue = "system") String actor) {
        int updated = webhookService.replay(deliveryId);
        auditService.record(
                "WEBHOOK_OPERATIONS", "WEBHOOK_REPLAY", "delivery:" + deliveryId, actor);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
