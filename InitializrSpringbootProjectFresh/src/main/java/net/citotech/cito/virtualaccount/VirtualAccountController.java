package net.citotech.cito.virtualaccount;

import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v2/merchant-self-service/virtual-accounts")
public class VirtualAccountController {
    private final VirtualAccountService virtualAccountService;
    private final MerchantSessionContext sessionContext;

    public VirtualAccountController(
            VirtualAccountService virtualAccountService, MerchantSessionContext sessionContext) {
        this.virtualAccountService = virtualAccountService;
        this.sessionContext = sessionContext;
    }

    @PostMapping
    public ResponseEntity<?> issue(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    virtualAccountService.issue(
                            sessionContext.requireMerchantId(request),
                            text(body.get("environment")),
                            text(body.get("countryCode")),
                            text(body.get("currencyCode")),
                            text(body.get("accountType")),
                            text(body.get("accountName")),
                            text(body.get("customerReference")),
                            text(body.get("purposeReference")),
                            instant(body.get("expiresAt")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("VIRTUAL_ACCOUNT_REJECTED", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> accounts(
            @RequestParam(value = "environment", defaultValue = "SANDBOX") String environment,
            HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    virtualAccountService.accounts(
                            sessionContext.requireMerchantId(request), environment));
        } catch (PaymentGatewayException e) {
            return bad("VIRTUAL_ACCOUNT_QUERY_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/close")
    public ResponseEntity<?> close(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    virtualAccountService.close(
                            sessionContext.requireMerchantId(request),
                            text(body.get("accountReference")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return bad("VIRTUAL_ACCOUNT_CLOSE_REJECTED", e.getMessage());
        }
    }

    @GetMapping("/transfers")
    public ResponseEntity<?> transfers(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                virtualAccountService.transfers(sessionContext.requireMerchantId(request), limit));
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