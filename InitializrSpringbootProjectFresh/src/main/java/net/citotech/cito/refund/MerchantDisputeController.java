package net.citotech.cito.refund;

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
@RequestMapping("/api/v2/merchant-self-service/disputes")
public class MerchantDisputeController {
    private final PaymentDisputeService disputeService;
    private final MerchantSessionContext sessionContext;

    public MerchantDisputeController(
            PaymentDisputeService disputeService, MerchantSessionContext sessionContext) {
        this.disputeService = disputeService;
        this.sessionContext = sessionContext;
    }

    @PostMapping
    public ResponseEntity<?> open(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    disputeService.openDispute(
                            sessionContext.requireMerchantId(request),
                            text(body.get("transactionReference")),
                            text(body.get("disputeType")),
                            nullableDecimal(body.get("amount")),
                            text(body.get("currencyCode")),
                            text(body.get("reasonCode")),
                            text(body.get("customerReference")),
                            sessionContext.actor(request),
                            instant(body.get("dueAt"))));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "DISPUTE_REJECTED", "message", e.getMessage()));
        }
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                disputeService.disputes(sessionContext.requireMerchantId(request), limit));
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(
            @RequestParam("disputeReference") String disputeReference,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    disputeService.events(
                            sessionContext.requireMerchantId(request), disputeReference));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "DISPUTE_NOT_FOUND", "message", e.getMessage()));
        }
    }

    private BigDecimal nullableDecimal(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        return new BigDecimal(raw);
    }

    private Instant instant(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("dueAt must use ISO-8601 UTC format");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}