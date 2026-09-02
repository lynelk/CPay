package net.citotech.cito.gateway;

import java.util.Map;
import java.util.UUID;
import net.citotech.cito.treasury.ProviderTreasuryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Receives MTN's single-attempt asynchronous result for a transaction-specific callback URL. */
@RestController
@RequestMapping("/api/v2/provider-callbacks/mtn")
public class MtnMomoCallbackController {
    private final ProviderTreasuryService treasuryService;

    public MtnMomoCallbackController(ProviderTreasuryService treasuryService) {
        this.treasuryService = treasuryService;
    }

    @RequestMapping(
            path = "/{providerReference}",
            method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<Map<String, Object>> receive(
            @PathVariable String providerReference, @RequestBody Map<String, Object> body) {
        requireUuid(providerReference);
        Map<String, Object> reservation =
                treasuryService.resolveProviderCallback(
                        MtnMomoCredentialSchema.CHANNEL_CODE,
                        providerReference,
                        text(body.get("externalId")),
                        text(body.get("status")),
                        text(body.get("financialTransactionId")));
        return ResponseEntity.ok(
                Map.of(
                        "accepted", true,
                        "reservationId", reservation.get("id"),
                        "status", reservation.get("status")));
    }

    private void requireUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (Exception e) {
            throw new PaymentGatewayException("Invalid MTN callback reference");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
