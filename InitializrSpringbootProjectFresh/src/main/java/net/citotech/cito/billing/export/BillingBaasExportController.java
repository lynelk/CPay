package net.citotech.cito.billing.export;

import java.time.Instant;
import java.util.Map;
import net.citotech.cito.billing.baas.BillingBaasApiKeyService;
import net.citotech.cito.billing.baas.BillingBaasContext;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/native/billing/baas/exports")
public class BillingBaasExportController {
    private final BillingBaasApiKeyService apiKeyService;
    private final FocusExportService focusExportService;

    public BillingBaasExportController(
            BillingBaasApiKeyService apiKeyService, FocusExportService focusExportService) {
        this.apiKeyService = apiKeyService;
        this.focusExportService = focusExportService;
    }

    @GetMapping(value = "/focus", produces = "text/csv")
    public ResponseEntity<?> focus(
            @RequestHeader("X-Cito-Api-Key") String apiKey,
            @RequestHeader("X-Cito-Environment") String environment,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        try {
            BillingBaasContext context =
                    apiKeyService.authenticate(
                            apiKey,
                            environment,
                            "BILLING_READ",
                            requestId,
                            "GET",
                            "/api/v2/native/billing/baas/exports/focus");
            String csv = focusExportService.csv(context.billingTenantId(), from, to);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header("X-Cito-FOCUS-Version", FocusExportService.FOCUS_VERSION)
                    .header(
                            "Content-Disposition",
                            "attachment; filename=\"cito-focus-cost-usage.csv\"")
                    .body(csv);
        } catch (PaymentGatewayException e) {
            String message = e.getMessage() == null ? "FOCUS export failed" : e.getMessage();
            HttpStatus status =
                    message.contains("credential") || message.contains("X-Cito-Api-Key")
                            ? HttpStatus.UNAUTHORIZED
                            : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("code", "BILLING_FOCUS_EXPORT_REJECTED", "message", message));
        }
    }
}
