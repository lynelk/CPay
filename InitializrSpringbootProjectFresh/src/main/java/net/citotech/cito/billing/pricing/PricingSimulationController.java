package net.citotech.cito.billing.pricing;

import java.time.Instant;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlled pricing-simulation surface. The first production-safe mode compares the deterministic
 * billing rating result with the retained legacy payment charge for a closed time window. It does
 * not mutate price books or historical charges.
 */
@RestController
@RequestMapping("/api/v2/admin/billing/pricing/simulations")
@PreAuthorize("hasRole('ADMIN')")
public class PricingSimulationController {
    private final ChargeShadowComparisonService comparisonService;

    public PricingSimulationController(ChargeShadowComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @PostMapping("/shadow")
    public ResponseEntity<?> shadow(@RequestBody ShadowSimulationRequest request) {
        try {
            if (request == null
                    || request.windowStart() == null
                    || request.windowEnd() == null
                    || !request.windowEnd().isAfter(request.windowStart())) {
                throw new PaymentGatewayException(
                        "Pricing simulation requires windowEnd after windowStart");
            }
            return ResponseEntity.ok(
                    comparisonService.compare(request.windowStart(), request.windowEnd()));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(
                            Map.of(
                                    "code",
                                    "BILLING_PRICING_SIMULATION_REJECTED",
                                    "message",
                                    e.getMessage()));
        }
    }

    public record ShadowSimulationRequest(Instant windowStart, Instant windowEnd) {}
}
