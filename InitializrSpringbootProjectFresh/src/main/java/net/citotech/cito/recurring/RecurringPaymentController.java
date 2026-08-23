package net.citotech.cito.recurring;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
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
@RequestMapping("/api/v2/merchant-self-service/recurring")
public class RecurringPaymentController {
    private final RecurringPaymentService recurringService;
    private final MerchantSessionContext sessionContext;

    public RecurringPaymentController(
            RecurringPaymentService recurringService, MerchantSessionContext sessionContext) {
        this.recurringService = recurringService;
        this.sessionContext = sessionContext;
    }

    @PostMapping("/plans")
    public ResponseEntity<?> createPlan(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    recurringService.createPlan(
                            sessionContext.requireMerchantId(request),
                            text(body.get("planName")),
                            decimal(body.get("amount")),
                            text(body.get("currencyCode")),
                            text(body.get("intervalUnit")),
                            intValue(body.get("intervalCount"), 1),
                            intValue(body.get("retryCount"), 2),
                            intValue(body.get("retryIntervalHours"), 24),
                            intValue(body.get("gracePeriodDays"), 3),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("RECURRING_PLAN_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/plans")
    public ResponseEntity<?> plans(HttpServletRequest request) {
        return ResponseEntity.ok(recurringService.plans(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/mandates")
    public ResponseEntity<?> createMandate(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    recurringService.createMandate(
                            sessionContext.requireMerchantId(request),
                            text(body.get("customerReference")),
                            text(body.get("payerType")),
                            text(body.get("payerValue")),
                            text(body.get("channelCode")),
                            text(body.get("countryCode")),
                            text(body.get("currencyCode")),
                            text(body.get("environment")),
                            text(body.get("executionMode")),
                            text(body.get("providerMandateReference")),
                            text(body.get("consentReference")),
                            instant(body.get("expiresAt"))));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("PAYMENT_MANDATE_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/mandates")
    public ResponseEntity<?> mandates(HttpServletRequest request) {
        return ResponseEntity.ok(
                recurringService.mandates(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<?> createSubscription(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    recurringService.createSubscription(
                            sessionContext.requireMerchantId(request),
                            text(body.get("planReference")),
                            text(body.get("mandateReference")),
                            text(body.get("customerReference")),
                            instant(body.get("startAt")),
                            instant(body.get("endAt")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("SUBSCRIPTION_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/subscriptions/status")
    public ResponseEntity<?> setSubscriptionStatus(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    recurringService.setSubscriptionStatus(
                            sessionContext.requireMerchantId(request),
                            text(body.get("subscriptionReference")),
                            text(body.get("status"))));
        } catch (PaymentGatewayException e) {
            return bad("SUBSCRIPTION_UPDATE_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<?> subscriptions(HttpServletRequest request) {
        return ResponseEntity.ok(
                recurringService.subscriptions(sessionContext.requireMerchantId(request)));
    }

    @GetMapping("/charges")
    public ResponseEntity<?> charges(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                recurringService.charges(sessionContext.requireMerchantId(request), limit));
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(text(value));
    }

    private int intValue(Object value, int fallback) {
        return value == null || text(value).isEmpty()
                ? fallback
                : Integer.parseInt(String.valueOf(value));
    }

    private Instant instant(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Timestamp values must use ISO-8601 UTC format");
        }
    }
}