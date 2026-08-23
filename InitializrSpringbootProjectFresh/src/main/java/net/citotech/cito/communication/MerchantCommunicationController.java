package net.citotech.cito.communication;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.V2RequestSecurityException;
import net.citotech.cito.api.v2.V2RequestSecurityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Merchant/service communications API. It accepts a logical message and hands it to CPay's durable
 * communications outbox; provider routing, charging, retry and health handling stay inside CPay.
 */
@RestController
@RequestMapping(path = "/api/v2/communication/messages")
public class MerchantCommunicationController {

    private final MerchantCommunicationService communicationService;
    private final V2RequestSecurityService securityService;
    private final ObjectMapper objectMapper;

    public MerchantCommunicationController(
            MerchantCommunicationService communicationService,
            V2RequestSecurityService securityService,
            ObjectMapper objectMapper) {
        this.communicationService = communicationService;
        this.securityService = securityService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> send(@RequestBody String body, HttpServletRequest request) {
        try {
            SendRequest input = objectMapper.readValue(body, SendRequest.class);
            if (blank(input.merchantNumber())) {
                return error(HttpStatus.BAD_REQUEST, "INVALID_COMMUNICATION_REQUEST", "merchantNumber is required.");
            }
            Merchant merchant = securityService.verify(request, body, input.merchantNumber());
            if (merchant.getId() == null || merchant.getId() <= 0) {
                return error(HttpStatus.UNAUTHORIZED, "INVALID_MERCHANT", "Merchant identity is unavailable.");
            }
            String channel = blank(input.channel()) ? "SMS" : input.channel().trim().toUpperCase();
            if (!"SMS".equals(channel)) {
                return error(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CHANNEL", "This service endpoint currently supports SMS.");
            }
            String idempotencyKey = request.getHeader("X-CPay-Idempotency-Key");
            Map<String, Object> result = communicationService.enqueueSms(
                    merchant.getId(),
                    input.recipient(),
                    input.content(),
                    input.purpose(),
                    input.externalReference(),
                    idempotencyKey,
                    input.expiresInSeconds());
            return ResponseEntity.accepted().body(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Request authentication failed.");
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, "COMMUNICATION_REJECTED", e.getMessage());
        } catch (Exception e) {
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "COMMUNICATION_UNAVAILABLE", "Communication request could not be accepted.");
        }
    }

    @GetMapping(path = "/{reference}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> status(
            @PathVariable("reference") String reference,
            @RequestParam("merchantNumber") String merchantNumber,
            HttpServletRequest request) {
        try {
            Merchant merchant = securityService.verify(request, "", merchantNumber);
            if (merchant.getId() == null || merchant.getId() <= 0) {
                return error(HttpStatus.UNAUTHORIZED, "INVALID_MERCHANT", "Merchant identity is unavailable.");
            }
            Map<String, Object> result = communicationService.status(merchant.getId(), reference);
            if (result == null) {
                return error(HttpStatus.NOT_FOUND, "COMMUNICATION_NOT_FOUND", "Communication was not found.");
            }
            return ResponseEntity.ok(result);
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Request authentication failed.");
        } catch (Exception e) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_COMMUNICATION_REQUEST", "Communication status could not be read.");
        }
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .body(Map.of("code", code, "message", message == null ? "Request rejected" : message));
    }

    public record SendRequest(
            String merchantNumber,
            String channel,
            String recipient,
            String content,
            String purpose,
            String externalReference,
            Integer expiresInSeconds) {}
}
