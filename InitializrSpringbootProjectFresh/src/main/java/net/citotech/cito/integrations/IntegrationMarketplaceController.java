package net.citotech.cito.integrations;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.MerchantSessionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/integrations")
public class IntegrationMarketplaceController {
    private final IntegrationMarketplaceService marketplaceService;
    private final MerchantSessionContext sessionContext;

    public IntegrationMarketplaceController(
            IntegrationMarketplaceService marketplaceService, MerchantSessionContext sessionContext) {
        this.marketplaceService = marketplaceService;
        this.sessionContext = sessionContext;
    }

    @GetMapping("/catalog")
    public ResponseEntity<?> catalog(HttpServletRequest request) {
        sessionContext.requireUser(request);
        return ResponseEntity.ok(marketplaceService.catalog());
    }

    @PostMapping("/installations")
    public ResponseEntity<?> install(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.install(
                            sessionContext.requireMerchantId(request),
                            text(body.get("connectorCode")),
                            text(body.get("versionNumber")),
                            text(body.get("environment")),
                            text(body.get("displayName")),
                            text(body.get("credentialReference")),
                            text(body.get("configurationJson")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("INTEGRATION_INSTALL_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/installations")
    public ResponseEntity<?> installations(HttpServletRequest request) {
        return ResponseEntity.ok(
                marketplaceService.installations(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/installations/uninstall")
    public ResponseEntity<?> uninstall(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.uninstall(
                            sessionContext.requireMerchantId(request),
                            text(body.get("installationReference"))));
        } catch (PaymentGatewayException e) {
            return bad("INTEGRATION_UNINSTALL_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/mappings")
    public ResponseEntity<?> addMapping(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.addMapping(
                            sessionContext.requireMerchantId(request),
                            text(body.get("installationReference")),
                            text(body.get("objectType")),
                            text(body.get("sourceField")),
                            text(body.get("targetField")),
                            text(body.get("transformation")),
                            text(body.get("direction"))));
        } catch (PaymentGatewayException e) {
            return bad("INTEGRATION_MAPPING_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/mappings")
    public ResponseEntity<?> mappings(
            @RequestParam("installationReference") String installationReference,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.mappings(
                            sessionContext.requireMerchantId(request), installationReference));
        } catch (PaymentGatewayException e) {
            return bad("INTEGRATION_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<?> subscribe(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.subscribeEvent(
                            sessionContext.requireMerchantId(request),
                            text(body.get("installationReference")),
                            text(body.get("eventType")),
                            text(body.get("direction"))));
        } catch (PaymentGatewayException e) {
            return bad("INTEGRATION_SUBSCRIPTION_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> queueJob(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.queueJob(
                            sessionContext.requireMerchantId(request),
                            text(body.get("installationReference")),
                            text(body.get("idempotencyKey")),
                            text(body.get("jobType")),
                            text(body.get("objectReference")),
                            text(body.get("payloadJson")),
                            intValue(body.get("maxAttempts"), 5)));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("INTEGRATION_JOB_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/jobs")
    public ResponseEntity<?> jobs(
            @RequestParam("installationReference") String installationReference,
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    marketplaceService.jobs(
                            sessionContext.requireMerchantId(request), installationReference, limit));
        } catch (PaymentGatewayException e) {
            return bad("INTEGRATION_NOT_FOUND", e.getMessage());
        }
    }

    private int intValue(Object value, int fallback) {
        return value == null || text(value).isEmpty()
                ? fallback
                : Integer.parseInt(String.valueOf(value));
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}