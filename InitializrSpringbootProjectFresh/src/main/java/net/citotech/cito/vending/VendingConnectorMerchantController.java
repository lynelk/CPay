package net.citotech.cito.vending;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingCallbackCorrelationService;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService;
import net.citotech.cito.vending.connector.VendingDeviceCommandService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant-session management of OEM contracts, diagnostics and physical-station QR targets. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/vending")
public class VendingConnectorMerchantController {
    private final VendingConnectorConfigurationService configurations;
    private final VendingCallbackCorrelationService correlations;
    private final VendingHostedRentalService hosted;
    private final VendingDeviceCommandService commands;
    private final String appBaseUrl;

    public VendingConnectorMerchantController(
            VendingConnectorConfigurationService configurations,
            VendingCallbackCorrelationService correlations,
            VendingHostedRentalService hosted,
            VendingDeviceCommandService commands,
            @Value("${app.base.url:}") String appBaseUrl) {
        this.configurations = configurations;
        this.correlations = correlations;
        this.hosted = hosted;
        this.commands = commands;
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

    @GetMapping(path = "/connectors/{connectorCode}/operations")
    public ResponseEntity<?> operations(
            @PathVariable("connectorCode") String connectorCode, HttpServletRequest request) {
        return handle(request, merchantId -> configurations.operations(merchantId, connectorCode));
    }

    @PostMapping(path = "/connectors/{connectorCode}/operations/{commandType}")
    public ResponseEntity<?> saveOperation(
            @PathVariable("connectorCode") String connectorCode,
            @PathVariable("commandType") String commandType,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return handle(
                request,
                merchantId ->
                        configurations.saveOperation(merchantId, connectorCode, commandType, body));
    }

    @GetMapping(path = "/connectors/{connectorCode}/callback-correlation")
    public ResponseEntity<?> callbackCorrelation(
            @PathVariable("connectorCode") String connectorCode, HttpServletRequest request) {
        return handle(
                request,
                merchantId -> {
                    var mapping = correlations.mapping(merchantId, connectorCode);
                    return Map.of(
                            "connectorCode",
                            connectorCode.trim().toUpperCase(),
                            "callbackCommandReferenceField",
                            mapping.commandReferenceField(),
                            "callbackProviderReferenceField",
                            mapping.providerReferenceField());
                });
    }

    @PostMapping(path = "/connectors/{connectorCode}/callback-correlation")
    public ResponseEntity<?> saveCallbackCorrelation(
            @PathVariable("connectorCode") String connectorCode,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {
        return handle(
                request,
                merchantId ->
                        correlations.save(
                                merchantId,
                                connectorCode,
                                text(body.get("callbackCommandReferenceField")),
                                text(body.get("callbackProviderReferenceField"))));
    }

    @GetMapping(path = "/connectors/{connectorCode}/readiness")
    public ResponseEntity<?> readiness(
            @PathVariable("connectorCode") String connectorCode, HttpServletRequest request) {
        return handle(request, merchantId -> configurations.readiness(merchantId, connectorCode));
    }

    @PostMapping(path = "/connectors/{connectorCode}/rotate-callback-secret")
    public ResponseEntity<?> rotateCallbackSecret(
            @PathVariable("connectorCode") String connectorCode, HttpServletRequest request) {
        return handle(
                request,
                merchantId -> configurations.rotateCallbackSecret(merchantId, connectorCode));
    }

    @PostMapping(path = "/devices/{deviceCode}/probe")
    public ResponseEntity<?> probe(
            @PathVariable("deviceCode") String deviceCode,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {
        Map<String, Object> payload = body == null ? Map.of() : body;
        MerchantUser user;
        try {
            user = currentMerchantUser(request);
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CONNECTOR_REJECTED",
                                    "message",
                                    safe(e.getMessage())));
        }
        if (user.getMerchant_id() == null) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CONNECTOR_REJECTED",
                                    "message",
                                    "Merchant login is required"));
        }
        try {
            return ResponseEntity.ok(
                    commands.probe(
                            user.getMerchant_id(),
                            deviceCode,
                            text(payload.get("commandType")),
                            stringMap(payload.get("parameters")),
                            user.getEmail()));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CONNECTOR_REJECTED",
                                    "message",
                                    safe(e.getMessage())));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CONNECTOR_FAILED",
                                    "message",
                                    "Unable to execute manufacturer probe"));
        }
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
            if (user.getMerchant_id() == null) {
                throw new PaymentGatewayException("Merchant login is required");
            }
            return ResponseEntity.ok(operation.run(user.getMerchant_id()));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CONNECTOR_REJECTED",
                                    "message",
                                    safe(e.getMessage())));
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
        if (session == null
                || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user;
    }

    private Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        raw.forEach(
                (key, item) -> {
                    if (key != null) {
                        result.put(String.valueOf(key), item == null ? "" : String.valueOf(item));
                    }
                });
        return result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "Vending connector operation rejected" : value;
    }

    @FunctionalInterface
    private interface MerchantOperation {
        Object run(long merchantId);
    }
}
