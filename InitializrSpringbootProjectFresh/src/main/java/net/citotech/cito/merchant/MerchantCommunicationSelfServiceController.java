package net.citotech.cito.merchant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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

/** Merchant communication operations with tenant scope taken exclusively from the session. */
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
            long merchantId = merchantId(request);
            WhatsAppSendResult result =
                    whatsAppDeliveryService.send(
                            new WhatsAppSendRequest(
                                    merchantId,
                                    text(body.get("recipients")),
                                    text(body.get("content"))));
            // Provider trace/body are deliberately not returned to merchant users.
            return ResponseEntity.ok(
                    Map.of("status", result.status(), "successful", result.successful()));
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", ex.getMessage()));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                    .body(error("WHATSAPP_SEND_REJECTED", ex.getMessage()));
        }
    }

    @GetMapping(path = "/ussd/sessions")
    public ResponseEntity<?> ussdSessions(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(ussdSessionService.recentSessions(merchantId(request)));
        } catch (PaymentGatewayException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("MERCHANT_SESSION_REQUIRED", ex.getMessage()));
        }
    }

    private long merchantId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("merchantUser") instanceof MerchantUser user)) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        if (user.getMerchant_id() == null || user.getMerchant_id() <= 0) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user.getMerchant_id();
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
