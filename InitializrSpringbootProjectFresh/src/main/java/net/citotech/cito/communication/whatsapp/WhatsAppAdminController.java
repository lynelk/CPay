package net.citotech.cito.communication.whatsapp;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlled WhatsApp send surface for operations/admin workflows. */
@RestController
@RequestMapping(path = "/api/v2/admin/communication/whatsapp")
@PreAuthorize("hasRole('ADMIN')")
public class WhatsAppAdminController {
    private final WhatsAppDeliveryService deliveryService;

    public WhatsAppAdminController(WhatsAppDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    @PostMapping(path = "/send")
    public ResponseEntity<?> send(@RequestBody Map<String, Object> body) {
        try {
            long merchantId = Long.parseLong(String.valueOf(body.get("merchantId")));
            String recipients = String.valueOf(body.getOrDefault("recipients", ""));
            String content = String.valueOf(body.getOrDefault("content", ""));
            WhatsAppSendResult result =
                    deliveryService.send(new WhatsAppSendRequest(merchantId, recipients, content));

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", result.status());
            response.put("successful", result.successful());
            // Raw provider response/trace deliberately stay server-side. Merchant-safe/admin UI
            // responses should not become a new path for provider data leakage.
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "WHATSAPP_SEND_REJECTED", "message", ex.getMessage()));
        }
    }
}
