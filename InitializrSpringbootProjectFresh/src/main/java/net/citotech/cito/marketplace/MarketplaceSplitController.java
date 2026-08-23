package net.citotech.cito.marketplace;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.platform.MerchantSessionContext;
import net.citotech.cito.platform.PlatformFeatureEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/merchant-self-service/marketplace")
public class MarketplaceSplitController {
    private final MarketplaceSplitService splitService;
    private final MarketplaceSplitSimulationService simulationService;
    private final MarketplaceRefundAllocationService refundAllocationService;
    private final PlatformFeatureEventService featureEventService;
    private final MerchantSessionContext sessionContext;

    public MarketplaceSplitController(
            MarketplaceSplitService splitService,
            MarketplaceSplitSimulationService simulationService,
            MarketplaceRefundAllocationService refundAllocationService,
            PlatformFeatureEventService featureEventService,
            MerchantSessionContext sessionContext) {
        this.splitService = splitService;
        this.simulationService = simulationService;
        this.refundAllocationService = refundAllocationService;
        this.featureEventService = featureEventService;
        this.sessionContext = sessionContext;
    }

    @GetMapping("/subaccounts")
    public ResponseEntity<?> subaccounts(HttpServletRequest request) {
        return ResponseEntity.ok(splitService.subaccounts(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/subaccounts")
    public ResponseEntity<?> createSubaccount(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    splitService.createSubaccount(
                            sessionContext.requireMerchantId(request),
                            text(body.get("displayName")),
                            text(body.get("currencyCode")),
                            text(body.get("destinationType")),
                            text(body.get("destinationReference")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SUBACCOUNT_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/subaccounts/status")
    public ResponseEntity<?> setSubaccountStatus(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    splitService.setSubaccountStatus(
                            sessionContext.requireMerchantId(request),
                            text(body.get("subaccountReference")),
                            text(body.get("status"))));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SUBACCOUNT_UPDATE_REJECTED", "message", e.getMessage()));
        }
    }

    @GetMapping("/split-rules")
    public ResponseEntity<?> splitRules(HttpServletRequest request) {
        return ResponseEntity.ok(splitService.splitRules(sessionContext.requireMerchantId(request)));
    }

    @PostMapping("/split-rules")
    public ResponseEntity<?> createSplitRule(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    splitService.createSplitRule(
                            sessionContext.requireMerchantId(request),
                            text(body.get("ruleName")),
                            text(body.get("currencyCode")),
                            text(body.get("allocationMode")),
                            text(body.get("platformFeeType")),
                            decimal(body.get("platformFeeValue")),
                            text(body.get("feeBearer")),
                            recipients(body.get("recipients")),
                            sessionContext.actor(request)));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SPLIT_RULE_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/split-rules/simulate")
    public ResponseEntity<?> simulateSplit(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(
                    simulationService.simulate(
                            sessionContext.requireMerchantId(request),
                            text(body.get("splitRuleReference")),
                            text(body.get("currencyCode")),
                            decimal(body.get("grossAmount"))));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "SPLIT_SIMULATION_REJECTED", "message", e.getMessage()));
        }
    }

    @GetMapping("/executions")
    public ResponseEntity<?> executions(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                splitService.executions(sessionContext.requireMerchantId(request), limit));
    }

    @GetMapping("/refund-allocations")
    public ResponseEntity<?> refundAllocations(
            @RequestParam("refundReference") String refundReference,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                refundAllocationService.allocations(
                        sessionContext.requireMerchantId(request), refundReference));
    }

    @GetMapping("/recovery-events")
    public ResponseEntity<?> recoveryEvents(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                featureEventService.recentEvents(sessionContext.requireMerchantId(request), limit));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> recipients(Object value) {
        if (!(value instanceof List<?> list)) {
            throw new PaymentGatewayException("recipients must be an array");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?>)) {
                throw new PaymentGatewayException("Each recipient must be an object");
            }
        }
        return (List<Map<String, Object>>) value;
    }

    private BigDecimal decimal(Object value) {
        if (value == null || text(value).isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException e) {
            throw new PaymentGatewayException("Amount values must be numeric");
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
