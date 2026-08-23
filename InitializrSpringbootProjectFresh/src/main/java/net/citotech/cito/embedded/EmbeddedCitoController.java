package net.citotech.cito.embedded;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.MerchantSessionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/embedded")
public class EmbeddedCitoController {
    private final EmbeddedCitoService embeddedService;
    private final MerchantSessionContext sessionContext;

    public EmbeddedCitoController(
            EmbeddedCitoService embeddedService, MerchantSessionContext sessionContext) {
        this.embeddedService = embeddedService;
        this.sessionContext = sessionContext;
    }

    @PostMapping("/partner")
    public ResponseEntity<?> ensurePartner(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.ensurePartner(
                            sessionContext.requireMerchantId(request),
                            text(body.get("partnerName")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("EMBEDDED_PARTNER_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/brand")
    public ResponseEntity<?> saveBrand(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.saveBrand(
                            sessionContext.requireMerchantId(request),
                            text(body.get("brandName")),
                            text(body.get("logoUrl")),
                            text(body.get("primaryColor")),
                            text(body.get("supportEmail")),
                            text(body.get("customDomain")),
                            text(body.get("termsUrl")),
                            text(body.get("privacyUrl")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("EMBEDDED_BRAND_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/onboarding-sessions")
    public ResponseEntity<?> onboardingSession(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.createOnboardingSession(
                            sessionContext.requireMerchantId(request),
                            text(body.get("intendedEmail")),
                            strings(body.get("serviceCodes")),
                            text(body.get("returnUrl")),
                            instant(body.get("expiresAt")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("EMBEDDED_SESSION_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/downstream-merchants")
    public ResponseEntity<?> linkDownstreamMerchant(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.linkDownstreamMerchant(
                            sessionContext.requireMerchantId(request),
                            longValue(body.get("downstreamMerchantId")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("DOWNSTREAM_MERCHANT_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/downstream-merchants")
    public ResponseEntity<?> downstreamMerchants(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.downstreamMerchants(
                            sessionContext.requireMerchantId(request)));
        } catch (PaymentGatewayException e) {
            // A merchant with no partner profile is in a normal first-use state, not an error.
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/delegations")
    public ResponseEntity<?> delegate(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.delegateService(
                            sessionContext.requireMerchantId(request),
                            longValue(body.get("downstreamMerchantId")),
                            text(body.get("serviceCode")),
                            text(body.get("environment")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("SERVICE_DELEGATION_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/delegations")
    public ResponseEntity<?> delegations(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.delegations(sessionContext.requireMerchantId(request)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.ok(List.of());
        }
    }

    @PostMapping("/commissions")
    public ResponseEntity<?> commission(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    embeddedService.saveCommission(
                            sessionContext.requireMerchantId(request),
                            text(body.get("serviceCode")),
                            text(body.get("commissionType")),
                            decimal(body.get("commissionValue")),
                            text(body.get("currencyCode")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("COMMISSION_RULE_REJECTED", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> list)) {
            throw new PaymentGatewayException("serviceCodes must be an array");
        }
        for (Object item : list) {
            if (!(item instanceof String)) {
                throw new PaymentGatewayException("Each service code must be a string");
            }
        }
        return (List<String>) value;
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(text(value));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(text(value));
    }

    private Instant instant(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("expiresAt must use ISO-8601 UTC format");
        }
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
