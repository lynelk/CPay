package net.citotech.cito.vending;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant-session management of manufacturer contracts and physical-station QR targets. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/vending")
public class VendingConnectorMerchantController {
    private final VendingConnectorConfigurationService configurations;
    private final VendingHostedRentalService hosted;
    private final String appBaseUrl;

    public VendingConnectorMerchantController(
            VendingConnectorConfigurationService configurations,
            VendingHostedRentalService hosted,
            @Value("${app.base-url:}") String appBaseUrl) {
        this.configurations = configurations;
        this.hosted = hosted;
        this.appBaseUrl = appBaseUrl;
    }

    @GetMapping(path = "/connectors")
    public ResponseEntity<?> connectors(HttpServletRequest request) {
        return handle(request, merchantId -> configurations.list(merchantId));
    }

    @PostMapping(path = "/connectors/{connectorCode}")
    public ResponseEntity<?> save(
            @PathVariable("connectorCode") String connectorCode,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return handle(request, merchantId -> configurations.save(merchantId, connectorCode, body));
    }

    @PostMapping(path = "/connectors/{connectorCode}/rotate-callback-secret")
    public ResponseEntity<?> rotateCallbackSecret(
            @PathVariable("connectorCode") String connectorCode, HttpServletRequest request) {
        return handle(
                request,
                merchantId -> configurations.rotateCallbackSecret(merchantId, connectorCode));
    }

    @PostMapping(path = "/devices/{deviceCode}/rotate-public-token")
    public ResponseEntity<?> rotatePublicToken(
            @PathVariable("deviceCode") String deviceCode, HttpServletRequest request) {
        return handle(
                request,
                merchantId -> hosted.rotateDevicePublicToken(merchantId, deviceCode, appBaseUrl));
    }

    private ResponseEntity<?> handle(HttpServletRequest request, MerchantOperation operation) {
        try {
            MerchantUser user = currentMerchantUser(request);
            if (user.getMerchant_id() == null) throw new PaymentGatewayException("Merchant login is required");
            return ResponseEntity.ok(operation.run(user.getMerchant_id()));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "VENDING_CONNECTOR_REJECTED", "message", safe(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CONNECTOR_FAILED",
                                    "message",
                                    "Unable to complete vending connector operation"));
        }
    }

    private MerchantUser currentMerchantUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Vending connector operation rejected" : value;
    }

    @FunctionalInterface
    private interface MerchantOperation {
        Object run(long merchantId);
    }
}
