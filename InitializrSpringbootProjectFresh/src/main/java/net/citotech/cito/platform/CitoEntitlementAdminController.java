package net.citotech.cito.platform;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/cito")
@PreAuthorize("hasRole('ADMIN')")
public class CitoEntitlementAdminController {
    private final CitoEntitlementService entitlementService;

    public CitoEntitlementAdminController(CitoEntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }

    @GetMapping("/service-catalog")
    public ResponseEntity<?> serviceCatalog() {
        return ResponseEntity.ok(entitlementService.serviceCatalog());
    }

    @GetMapping("/entitlements")
    public ResponseEntity<?> entitlements(@RequestParam("merchantId") long merchantId) {
        return ResponseEntity.ok(entitlementService.entitlementsForMerchant(merchantId));
    }

    @PostMapping("/entitlements")
    public ResponseEntity<?> setEntitlement(@RequestBody Map<String, Object> body) {
        try {
            long merchantId = longValue(body.get("merchantId"), "merchantId");
            return ResponseEntity.ok(
                    entitlementService.setEntitlement(
                            merchantId,
                            text(body.get("serviceCode")),
                            text(body.get("environment")),
                            text(body.get("status")),
                            text(body.get("planCode")),
                            instant(body.get("startsAt")),
                            instant(body.get("endsAt")),
                            text(body.get("actor"))));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ENTITLEMENT_UPDATE_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/access-reviews")
    public ResponseEntity<?> createAccessReview(@RequestBody Map<String, Object> body) {
        try {
            long merchantId = longValue(body.get("merchantId"), "merchantId");
            return ResponseEntity.ok(
                    entitlementService.createAccessReview(
                            merchantId,
                            instant(body.get("dueAt")),
                            text(body.get("requestedBy")),
                            text(body.get("notes"))));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ACCESS_REVIEW_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/access-reviews/complete")
    public ResponseEntity<?> completeAccessReview(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    entitlementService.completeAccessReview(
                            text(body.get("reviewReference")),
                            text(body.get("status")),
                            text(body.get("reviewer")),
                            text(body.get("notes"))));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ACCESS_REVIEW_REJECTED", "message", e.getMessage()));
        }
    }

    @GetMapping("/access-events")
    public ResponseEntity<?> accessEvents(
            @RequestParam("merchantId") long merchantId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ResponseEntity.ok(entitlementService.accessEvents(merchantId, limit));
    }

    private long longValue(Object value, String field) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(text(value));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field + " must be a positive integer");
        }
    }

    private Instant instant(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Timestamp values must use ISO-8601 UTC format");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}