package net.citotech.cito.webhook;

import java.util.Map;
import java.util.UUID;
import net.citotech.cito.api.v2.dto.ApiErrorResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/api/v2/admin/webhooks")
public class MerchantWebhookController {
    private final MerchantWebhookService webhookService;

    public MerchantWebhookController(MerchantWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    @PostMapping(path = "/merchants/{merchantId}")
    public ResponseEntity<?> register(@PathVariable("merchantId") long merchantId,
                                      @RequestBody Map<String, String> body) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(webhookService.registerEndpoint(
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

    @PostMapping(path = "/{endpointId}/rotate-secret")
    public ResponseEntity<?> rotate(@PathVariable("endpointId") long endpointId) {
        try {
            return ResponseEntity.ok(webhookService.rotateSecret(endpointId));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "WEBHOOK_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping(path = "/deliveries/{deliveryId}/replay")
    public ResponseEntity<?> replay(@PathVariable("deliveryId") long deliveryId) {
        return ResponseEntity.ok(Map.of("updated", webhookService.replay(deliveryId)));
    }

    private ResponseEntity<ApiErrorResponse> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(code, message, UUID.randomUUID().toString()));
    }
}
