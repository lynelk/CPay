package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.communication.ussd.UssdSessionService;
import net.citotech.cito.communication.whatsapp.WhatsAppDeliveryService;
import net.citotech.cito.communication.whatsapp.WhatsAppSendRequest;
import net.citotech.cito.communication.whatsapp.WhatsAppSendResult;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Merchant communication operations authorized exclusively by MerchantRole. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/communication")
public class MerchantCommunicationSelfServiceController {
    private final WhatsAppDeliveryService whatsAppDeliveryService;
    private final UssdSessionService ussdSessionService;

    public MerchantCommunicationSelfServiceController(
            WhatsAppDeliveryService whatsAppDeliveryService, UssdSessionService ussdSessionService) {
        this.whatsAppDeliveryService = whatsAppDeliveryService;
        this.ussdSessionService = ussdSessionService;
    }

    @PostMapping(path = "/whatsapp/send")
    public ResponseEntity<?> sendWhatsApp(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            long merchantId = merchantIdWithCommunicationAccess(request);
            WhatsAppSendResult result =
                    whatsAppDeliveryService.send(
                            new WhatsAppSendRequest(
                                    merchantId,
                                    text(body.get("recipients")),
                                    text(body.get("content"))));
            return ResponseEntity.ok(
                    Map.of("status", result.status(), "successful", result.successful()));
        } catch (PaymentGatewayException ex) {
            return forbidden(ex);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                    .body(error("WHATSAPP_SEND_REJECTED", ex.getMessage()));
        }
    }

    @GetMapping(path = "/ussd/sessions")
    public ResponseEntity<?> ussdSessions(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    ussdSessionService.recentSessions(merchantIdWithCommunicationAccess(request)));
        } catch (PaymentGatewayException ex) {
            return forbidden(ex);
        }
    }

    private long merchantIdWithCommunicationAccess(HttpServletRequest request) {
        MerchantUser user = MerchantAuthorization.requireCapability(request, "COMMUNICATION");
        return user.getMerchant_id();
    }

    private ResponseEntity<?> forbidden(PaymentGatewayException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(error("MERCHANT_COMMUNICATION_FORBIDDEN", ex.getMessage()));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private Map<String, Object> error(String code, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("message", message);
        return result;
    }
}
