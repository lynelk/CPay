package net.citotech.cito.vending.connector;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public provider-facing endpoint; authentication is the connector-specific HMAC contract. */
@RestController
@RequestMapping(path = "/api/v2/vending/device-callbacks")
public class VendingDeviceCallbackController {
    private final VendingCallbackSecurityService security;
    private final VendingDeviceEventService events;

    public VendingDeviceCallbackController(
            VendingCallbackSecurityService security, VendingDeviceEventService events) {
        this.security = security;
        this.events = events;
    }

    @PostMapping(
            path = "/{connectorCode}/{merchantId}",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> callback(
            @PathVariable("connectorCode") String connectorCode,
            @PathVariable("merchantId") long merchantId,
            @RequestBody String rawBody,
            HttpServletRequest request) {
        try {
            Contract contract = security.verify(merchantId, connectorCode, request, rawBody);
            return ResponseEntity.ok(events.process(merchantId, contract, rawBody));
        } catch (PaymentGatewayException e) {
            String message = e.getMessage() == null ? "Vending callback rejected" : e.getMessage();
            HttpStatus status =
                    message.toLowerCase().contains("signature")
                                    || message.toLowerCase().contains("nonce")
                                    || message.toLowerCase().contains("timestamp")
                            ? HttpStatus.UNAUTHORIZED
                            : HttpStatus.BAD_REQUEST;
            return ResponseEntity.status(status)
                    .body(Map.of("code", "VENDING_CALLBACK_REJECTED", "message", message));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(
                            Map.of(
                                    "code",
                                    "VENDING_CALLBACK_FAILED",
                                    "message",
                                    "Unable to process vending device callback"));
        }
    }
}
