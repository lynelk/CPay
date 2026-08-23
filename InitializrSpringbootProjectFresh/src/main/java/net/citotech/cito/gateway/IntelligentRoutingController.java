package net.citotech.cito.gateway;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.platform.MerchantSessionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/routing")
public class IntelligentRoutingController {
    private final IntelligentPaymentRoutingService routingService;
    private final MerchantSessionContext sessionContext;

    public IntelligentRoutingController(
            IntelligentPaymentRoutingService routingService,
            MerchantSessionContext sessionContext) {
        this.routingService = routingService;
        this.sessionContext = sessionContext;
    }

    @PostMapping("/simulate")
    public ResponseEntity<?> simulate(
            @RequestParam("operation") String operation,
            @RequestBody PaymentRequest paymentRequest,
            HttpServletRequest request) {
        try {
            MerchantUser user = sessionContext.requireUser(request);
            String account = account(paymentRequest, operation);
            return ResponseEntity.ok(
                    routingService.simulate(
                            paymentRequest, user.getMerchant_number(), operation, account));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ROUTING_SIMULATION_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/policy")
    public ResponseEntity<?> savePolicy(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            MerchantUser user = sessionContext.requireUser(request);
            return ResponseEntity.ok(
                    routingService.savePolicy(
                            user.getMerchant_number(),
                            text(body.get("operation")),
                            text(body.get("countryCode")),
                            text(body.get("currencyCode")),
                            text(body.get("strategy")),
                            booleanValue(body.get("fallbackAllowed"), true),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ROUTING_POLICY_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/rule")
    public ResponseEntity<?> saveRule(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            sessionContext.requireUser(request);
            return ResponseEntity.ok(
                    routingService.saveRule(
                            text(body.get("policyCode")),
                            text(body.get("channelCode")),
                            intValue(body.get("priorityRank"), 100),
                            decimal(body.get("weight"), BigDecimal.ONE),
                            decimal(body.get("costScore"), BigDecimal.ZERO),
                            nullableDecimal(body.get("minSuccessRate")),
                            nullableLong(body.get("maxLatencyMs")),
                            booleanValue(body.get("active"), true)));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ROUTING_RULE_REJECTED", "message", e.getMessage()));
        }
    }

    @GetMapping("/decisions")
    public ResponseEntity<?> decisions(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        MerchantUser user = sessionContext.requireUser(request);
        return ResponseEntity.ok(routingService.decisions(user.getMerchant_number(), limit));
    }

    private String account(PaymentRequest request, String operation) {
        if (request == null) {
            return "";
        }
        if ("PAYOUT".equalsIgnoreCase(operation)) {
            return request.getPayee() == null ? "" : request.getPayee().getValue();
        }
        return request.getPayer() == null ? "" : request.getPayer().getValue();
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean booleanValue(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intValue(Object value, int fallback) {
        return value == null ? fallback : Integer.parseInt(String.valueOf(value));
    }

    private BigDecimal decimal(Object value, BigDecimal fallback) {
        return value == null ? fallback : new BigDecimal(String.valueOf(value));
    }

    private BigDecimal nullableDecimal(Object value) {
        return value == null || text(value).isEmpty() ? null : new BigDecimal(String.valueOf(value));
    }

    private Long nullableLong(Object value) {
        return value == null || text(value).isEmpty() ? null : Long.parseLong(String.valueOf(value));
    }
}