package net.citotech.cito.billing.baas;

import java.time.Instant;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/native/billing/baas")
public class BillingBaasBillingCenterController {
    private final BillingBaasApiKeyService apiKeyService;
    private final BillingBaasBillingCenterService service;

    public BillingBaasBillingCenterController(
            BillingBaasApiKeyService apiKeyService, BillingBaasBillingCenterService service) {
        this.apiKeyService = apiKeyService;
        this.service = service;
    }

    @GetMapping("/invoices")
    public ResponseEntity<?> invoices(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        try {
            BillingBaasContext context = context(apiKey, environment, requestId, "/invoices");
            return ResponseEntity.ok(service.invoices(context, status, limit));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/invoices/{invoiceNumber}")
    public ResponseEntity<?> invoice(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @PathVariable String invoiceNumber) {
        try {
            BillingBaasContext context = context(apiKey, environment, requestId, "/invoices/{invoiceNumber}");
            return ResponseEntity.ok(service.invoice(context, invoiceNumber));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/quotas")
    public ResponseEntity<?> quotas(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            BillingBaasContext context = context(apiKey, environment, requestId, "/quotas");
            return ResponseEntity.ok(service.quota(context));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/entitlements")
    public ResponseEntity<?> entitlements(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
        try {
            BillingBaasContext context = context(apiKey, environment, requestId, "/entitlements");
            return ResponseEntity.ok(service.entitlements(context));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam(value = "asOf", required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
                    Instant asOf) {
        try {
            BillingBaasContext context = context(apiKey, environment, requestId, "/catalog");
            return ResponseEntity.ok(service.catalog(context, asOf));
        } catch (PaymentGatewayException e) {
            return error(e);
        }
    }

    private BillingBaasContext context(
            String apiKey, String environment, String requestId, String route) {
        return apiKeyService.authenticate(
                apiKey,
                environment,
                "BILLING_READ",
                requestId,
                "GET",
                "/api/v2/native/billing/baas" + route);
    }

    private ResponseEntity<?> error(PaymentGatewayException e) {
        String message = e.getMessage() == null ? "BaaS Billing Center request failed" : e.getMessage();
        HttpStatus status =
                message.contains("credential") || message.contains("X-Cito-Api-Key")
                        ? HttpStatus.UNAUTHORIZED
                        : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
                .body(Map.of("code", "BILLING_BAAS_BILLING_CENTER_REJECTED", "message", message));
    }
}
