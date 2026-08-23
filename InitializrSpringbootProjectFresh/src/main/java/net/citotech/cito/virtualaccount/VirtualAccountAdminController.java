package net.citotech.cito.virtualaccount;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/virtual-accounts")
@PreAuthorize("hasRole('ADMIN')")
public class VirtualAccountAdminController {
    private final VirtualAccountService virtualAccountService;

    public VirtualAccountAdminController(VirtualAccountService virtualAccountService) {
        this.virtualAccountService = virtualAccountService;
    }

    @PostMapping("/providers")
    public ResponseEntity<?> configureProvider(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    virtualAccountService.configureProvider(
                            text(body.get("providerCode")),
                            text(body.get("providerName")),
                            text(body.get("countryCode")),
                            text(body.get("currencyCode")),
                            text(body.get("environment")),
                            text(body.get("providerType")),
                            text(body.get("connectorReference")),
                            bool(body.get("certified")),
                            bool(body.get("active")),
                            text(body.get("actor"))));
        } catch (PaymentGatewayException e) {
            return bad("VIRTUAL_PROVIDER_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/production-accounts")
    public ResponseEntity<?> registerProductionAccount(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    virtualAccountService.registerExternalProductionAccount(
                            longValue(body.get("merchantId")),
                            text(body.get("providerCode")),
                            text(body.get("countryCode")),
                            text(body.get("currencyCode")),
                            text(body.get("accountType")),
                            text(body.get("accountName")),
                            text(body.get("accountNumber")),
                            text(body.get("bankCode")),
                            text(body.get("bankName")),
                            text(body.get("customerReference")),
                            text(body.get("purposeReference")),
                            instant(body.get("expiresAt")),
                            text(body.get("actor"))));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return bad("PRODUCTION_VIRTUAL_ACCOUNT_REJECTED", e.getMessage());
        }
    }

    @PostMapping("/incoming-transfers")
    public ResponseEntity<?> incomingTransfer(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    virtualAccountService.recordIncomingTransfer(
                            text(body.get("providerCode")),
                            text(body.get("countryCode")),
                            text(body.get("currencyCode")),
                            text(body.get("environment")),
                            text(body.get("accountNumber")),
                            text(body.get("providerTransferReference")),
                            decimal(body.get("amount")),
                            text(body.get("senderName")),
                            text(body.get("senderReference")),
                            text(body.get("narration"))));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("INCOMING_TRANSFER_REJECTED", e.getMessage());
        }
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private boolean bool(Object value) {
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(text(value));
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(text(value));
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
}