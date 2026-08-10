package net.citotech.cito.vending;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.ChargeNowSandboxSetupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** One-shot merchant workflow for applying and inspecting the ChargeNow OEM sandbox contract. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/vending/connectors/CHARGENOW/sandbox")
public class VendingChargeNowSandboxController {
    private final ChargeNowSandboxSetupService sandbox;
    private final String appBaseUrl;

    public VendingChargeNowSandboxController(
            ChargeNowSandboxSetupService sandbox,
            @Value("${app.base.url:}") String appBaseUrl) {
        this.sandbox = sandbox;
        this.appBaseUrl = appBaseUrl;
    }

    @GetMapping(path = "/manifest")
    public ResponseEntity<?> manifest(HttpServletRequest request) {
        return handle(request, sandbox::manifest);
    }

    @PostMapping(path = "/apply")
    public ResponseEntity<?> apply(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return handle(request, merchantId -> sandbox.apply(merchantId, body));
    }

    private ResponseEntity<?> handle(HttpServletRequest request, MerchantOperation operation) {
        try {
            MerchantUser user = currentMerchantUser(request);
            if (user.getMerchant_id() == null) {
                throw new PaymentGatewayException("Merchant login is required");
            }
            return ResponseEntity.ok(withCallbackUrl(operation.run(user.getMerchant_id())));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "CHARGENOW_SANDBOX_REJECTED",
                                    "message",
                                    safe(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "code",
                                    "CHARGENOW_SANDBOX_FAILED",
                                    "message",
                                    "Unable to complete ChargeNow sandbox setup"));
        }
    }

    private Map<String, Object> withCallbackUrl(Object value) {
        if (!(value instanceof Map<?, ?> raw)) {
            return Map.of("result", value);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach(
                (key, item) -> {
                    if (key != null) result.put(String.valueOf(key), item);
                });
        String path = String.valueOf(result.getOrDefault("callbackPath", ""));
        result.put("callbackUrl", absolute(path));
        return result;
    }

    private String absolute(String path) {
        String base = appBaseUrl == null ? "" : appBaseUrl.trim();
        if (base.isBlank()) return path;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return path.startsWith("/") ? base + path : base + "/" + path;
    }

    private MerchantUser currentMerchantUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null
                || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "ChargeNow sandbox setup rejected" : value;
    }

    @FunctionalInterface
    private interface MerchantOperation {
        Object run(long merchantId);
    }
}
