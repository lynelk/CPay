package net.citotech.cito.vending;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Merchant-session surface for the multi-tenant vending domain. */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/vending")
public class VendingMerchantController {
    private final VendingRepository repository;
    private final VendingRentalService rentals;

    public VendingMerchantController(VendingRepository repository, VendingRentalService rentals) {
        this.repository = repository;
        this.rentals = rentals;
    }

    @GetMapping(path = "/overview")
    public ResponseEntity<?> overview(HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) -> {
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("locations", repository.locations(merchantId).size());
                    response.put("devices", repository.devices(merchantId).size());
                    response.put("pricingPolicies", repository.pricingPolicies(merchantId).size());
                    response.put("recentRentals", repository.rentals(merchantId, 25));
                    return response;
                });
    }

    @GetMapping(path = "/locations")
    public ResponseEntity<?> locations(HttpServletRequest request) {
        return handle(request, (merchantId, user) -> repository.locations(merchantId));
    }

    @PostMapping(path = "/locations")
    public ResponseEntity<?> createLocation(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) ->
                        repository.createLocation(
                                merchantId,
                                text(body.get("locationCode")),
                                text(body.get("name")),
                                text(body.get("address"))));
    }

    @GetMapping(path = "/pricing")
    public ResponseEntity<?> pricing(HttpServletRequest request) {
        return handle(request, (merchantId, user) -> repository.pricingPolicies(merchantId));
    }

    @PostMapping(path = "/pricing")
    public ResponseEntity<?> createPricing(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) ->
                        repository.createPricingPolicy(
                                merchantId,
                                text(body.get("policyCode")),
                                text(body.get("name")),
                                text(body.get("currency")),
                                amount(body.get("depositAmount"), BigDecimal.ZERO),
                                integer(body.get("freeMinutes"), 0),
                                amount(body.get("unitPrice"), null),
                                integer(body.get("billingBlockMinutes"), 60),
                                integer(body.get("minimumBillingBlocks"), 1),
                                optionalAmount(body.get("dailyCapAmount")),
                                optionalAmount(body.get("overtimeAmount")),
                                optionalInteger(body.get("overtimeDays")),
                                text(body.get("refundMode"))));
    }

    @GetMapping(path = "/devices")
    public ResponseEntity<?> devices(HttpServletRequest request) {
        return handle(request, (merchantId, user) -> repository.devices(merchantId));
    }

    @PostMapping(path = "/devices")
    public ResponseEntity<?> createDevice(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) ->
                        repository.createDevice(
                                merchantId,
                                requiredLong(body.get("locationId"), "locationId"),
                                requiredLong(body.get("pricingPolicyId"), "pricingPolicyId"),
                                text(body.get("deviceCode")),
                                text(body.get("deviceType")),
                                text(body.get("connectorCode")),
                                text(body.get("externalDeviceId")),
                                integer(body.get("slotCount"), 0)));
    }

    @GetMapping(path = "/rentals")
    public ResponseEntity<?> rentalList(
            @RequestParam(name = "limit", required = false) Integer limit,
            HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) -> repository.rentals(merchantId, limit == null ? 100 : limit));
    }

    @PostMapping(path = "/rentals/start")
    public ResponseEntity<?> startRental(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) ->
                        rentals.startRental(
                                merchantId,
                                text(body.get("deviceCode")),
                                text(body.get("customerMsisdn")),
                                text(body.get("channel")),
                                text(body.get("reference")),
                                user.getEmail()));
    }

    @PostMapping(path = "/rentals/{reference}/sync")
    public ResponseEntity<?> sync(
            @PathVariable("reference") String reference, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) -> rentals.sync(merchantId, reference, user.getEmail()));
    }

    @PostMapping(path = "/rentals/{reference}/release")
    public ResponseEntity<?> release(
            @PathVariable("reference") String reference, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) -> rentals.release(merchantId, reference, user.getEmail()));
    }

    @PostMapping(path = "/rentals/{reference}/return")
    public ResponseEntity<?> returnRental(
            @PathVariable("reference") String reference, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) -> rentals.returnRental(merchantId, reference, user.getEmail()));
    }

    @PostMapping(path = "/surcharges/waive")
    public ResponseEntity<?> waiveSurcharge(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        return handle(
                request,
                (merchantId, user) ->
                        rentals.waiveSurcharge(
                                merchantId,
                                text(body.get("customerMsisdn")),
                                text(body.get("currency")),
                                optionalAmount(body.get("amount")),
                                text(body.get("note")),
                                user.getEmail()));
    }

    private ResponseEntity<?> handle(HttpServletRequest request, MerchantOperation operation) {
        try {
            MerchantUser user = currentMerchantUser(request);
            if (user.getMerchant_id() == null) {
                throw new PaymentGatewayException("Merchant login is required");
            }
            return ResponseEntity.ok(operation.run(user.getMerchant_id(), user));
        } catch (PaymentGatewayException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("VENDING_REJECTED", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error("VENDING_FAILED", "Unable to complete vending operation"));
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

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int integer(Object value, int fallback) {
        String raw = text(value);
        if (raw.isEmpty()) return fallback;
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Expected an integer value");
        }
    }

    private Integer optionalInteger(Object value) {
        String raw = text(value);
        return raw.isEmpty() ? null : integer(value, 0);
    }

    private long requiredLong(Object value, String name) {
        String raw = text(value);
        if (raw.isEmpty()) throw new PaymentGatewayException(name + " is required");
        try {
            long parsed = Long.parseLong(raw);
            if (parsed <= 0) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException(name + " must be a positive integer");
        }
    }

    private BigDecimal amount(Object value, BigDecimal fallback) {
        String raw = text(value).replace(",", "");
        if (raw.isEmpty()) {
            if (fallback != null) return fallback;
            throw new PaymentGatewayException("Amount is required");
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Expected a valid amount");
        }
    }

    private BigDecimal optionalAmount(Object value) {
        String raw = text(value);
        return raw.isEmpty() ? null : amount(value, null);
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "code", code, "message", message == null ? "Vending operation failed" : message);
    }

    @FunctionalInterface
    private interface MerchantOperation {
        Object run(long merchantId, MerchantUser user);
    }
}
