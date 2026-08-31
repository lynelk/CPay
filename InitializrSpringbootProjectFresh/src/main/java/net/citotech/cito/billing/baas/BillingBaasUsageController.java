package net.citotech.cito.billing.baas;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/native/billing/baas/usage")
public class BillingBaasUsageController {
    private final BillingBaasApiKeyService apiKeyService;
    private final BillingBaasUsageService usageService;

    public BillingBaasUsageController(
            BillingBaasApiKeyService apiKeyService, BillingBaasUsageService usageService) {
        this.apiKeyService = apiKeyService;
        this.usageService = usageService;
    }

    @PostMapping("/events")
    public ResponseEntity<?> ingest(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody UsageEventRequest body) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_WRITE", requestId, "POST", "/usage/events");
            return ResponseEntity.ok(
                    usageService.ingest(
                            context,
                            body.serviceCode(),
                            body.meterCode(),
                            body.eventTime(),
                            body.quantity(),
                            body.currency(),
                            body.dimensions(),
                            body.sourceReference(),
                            body.idempotencyKey()));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return error(e);
        }
    }

    @GetMapping("/events")
    public ResponseEntity<?> list(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to,
            @RequestParam(value = "serviceCode", required = false) String serviceCode,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_READ", requestId, "GET", "/usage/events");
            return ResponseEntity.ok(usageService.list(context, from, to, serviceCode, limit));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return error(e);
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<?> summary(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(value = "from", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant from,
            @RequestParam(value = "to", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant to) {
        try {
            BillingBaasContext context =
                    context(apiKey, environment, "BILLING_READ", requestId, "GET", "/usage/summary");
            return ResponseEntity.ok(usageService.summary(context, from, to));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
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

    private ResponseEntity<?> error(RuntimeException e) {
        String message = e.getMessage() == null ? "BaaS usage request failed" : e.getMessage();
        HttpStatus status =
                message.contains("credential") || message.contains("X-Cito-Api-Key")
                        ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("code", "BILLING_BAAS_USAGE_REJECTED", "message", message));
    }

    public record UsageEventRequest(
            String serviceCode,
            String meterCode,
            Instant eventTime,
            BigDecimal quantity,
            String currency,
            Map<String, String> dimensions,
            String sourceReference,
            String idempotencyKey) {}
}
