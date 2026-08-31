package net.citotech.cito.billing.baas;

import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.webhook.MerchantWebhookService;
import net.citotech.cito.webhook.WebhookEventCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * BaaS-facing management facade over Cito's existing durable merchant webhook delivery engine. This
 * deliberately reuses the proven endpoint/delivery/retry implementation instead of creating a
 * second webhook stack merely because a BaaS-specific subscription table happens to exist.
 */
@RestController
@RequestMapping("/api/v2/native/billing/baas/webhooks")
public class BillingBaasWebhookController {
    private final BillingBaasApiKeyService apiKeyService;
    private final MerchantWebhookService webhookService;

    public BillingBaasWebhookController(
            BillingBaasApiKeyService apiKeyService, MerchantWebhookService webhookService) {
        this.apiKeyService = apiKeyService;
        this.webhookService = webhookService;
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            context(apiKey, environment, "BILLING_READ", requestId, "GET", "/webhooks/events");
            return ResponseEntity.ok(WebhookEventCatalog.all());
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_READ", requestId, "GET", "/webhooks");
            return ResponseEntity.ok(webhookService.listEndpoints(context.merchantId()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping
    public ResponseEntity<?> create(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody WebhookRequest body) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_WRITE", requestId, "POST", "/webhooks");
            return ResponseEntity.ok(
                    webhookService.registerEndpoint(
                            context.merchantId(),
                            body.eventType(),
                            body.endpointUrl(),
                            "SERVICE_ACCOUNT:" + context.serviceAccountId()));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/{endpointId}/rotate-secret")
    public ResponseEntity<?> rotate(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long endpointId) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/webhooks/{endpointId}/rotate-secret");
            return ResponseEntity.ok(webhookService.rotateSecret(context.merchantId(), endpointId));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/deliveries")
    public ResponseEntity<?> deliveries(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_READ",
                            requestId,
                            "GET",
                            "/webhooks/deliveries");
            return ResponseEntity.ok(webhookService.listDeliveries(context.merchantId(), limit));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/deliveries/{deliveryId}/replay")
    public ResponseEntity<?> replay(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable long deliveryId) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/webhooks/deliveries/{deliveryId}/replay");
            int updated = webhookService.replay(context.merchantId(), deliveryId);
            if (updated == 0) {
                throw new PaymentGatewayException(
                        "Webhook delivery is not replayable for this tenant");
            }
            return ResponseEntity.ok(Map.of("code", "000", "requeued", true));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @PostMapping("/test")
    public ResponseEntity<?> test(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody WebhookTestRequest body) {
        try {
            BillingBaasContext context =
                    context(
                            apiKey,
                            environment,
                            "BILLING_WRITE",
                            requestId,
                            "POST",
                            "/webhooks/test");
            int queued = webhookService.testCallback(context.merchantId(), body.eventType());
            return ResponseEntity.ok(Map.of("code", "000", "queued", queued));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    private BillingBaasContext context(
            String apiKey,
            String environment,
            String scope,
            String requestId,
            String method,
            String route) {
        return apiKeyService.authenticate(
                apiKey,
                environment,
                scope,
                requestId,
                method,
                "/api/v2/native/billing/baas" + route);
    }

    private ResponseEntity<?> error(PaymentGatewayException e) {
        String message = e.getMessage() == null ? "BaaS webhook request failed" : e.getMessage();
        HttpStatus status =
                message.contains("credential") || message.contains("X-Cito-Api-Key")
                        ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("code", "BILLING_BAAS_WEBHOOK_REJECTED", "message", message));
    }

    public record WebhookRequest(String eventType, String endpointUrl) {}

    public record WebhookTestRequest(String eventType) {}
}
