package net.citotech.cito.platform;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/cito")
public class CitoEntitlementController {
    private final CitoEntitlementService entitlementService;
    private final MerchantSessionContext sessionContext;

    public CitoEntitlementController(
            CitoEntitlementService entitlementService, MerchantSessionContext sessionContext) {
        this.entitlementService = entitlementService;
        this.sessionContext = sessionContext;
    }

    @GetMapping("/service-catalog")
    public ResponseEntity<?> serviceCatalog(HttpServletRequest request) {
        try {
            sessionContext.requireUser(request);
            return ResponseEntity.ok(entitlementService.serviceCatalog());
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "MERCHANT_SESSION_REQUIRED", "message", e.getMessage()));
        }
    }

    @GetMapping("/entitlements")
    public ResponseEntity<?> entitlements(HttpServletRequest request) {
        try {
            long merchantId = sessionContext.requireMerchantId(request);
            return ResponseEntity.ok(entitlementService.entitlementsForMerchant(merchantId));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "MERCHANT_SESSION_REQUIRED", "message", e.getMessage()));
        }
    }

    @GetMapping("/entitlements/check")
    public ResponseEntity<?> entitlementCheck(
            @RequestParam("serviceCode") String serviceCode,
            @RequestParam(value = "environment", defaultValue = "SANDBOX") String environment,
            HttpServletRequest request) {
        try {
            long merchantId = sessionContext.requireMerchantId(request);
            return ResponseEntity.ok(
                    Map.of(
                            "serviceCode",
                            serviceCode,
                            "environment",
                            environment,
                            "active",
                            entitlementService.hasEntitlement(
                                    merchantId, serviceCode, environment)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "ENTITLEMENT_CHECK_REJECTED", "message", e.getMessage()));
        }
    }
}