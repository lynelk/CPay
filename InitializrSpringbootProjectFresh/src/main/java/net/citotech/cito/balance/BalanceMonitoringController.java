package net.citotech.cito.balance;

import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin ops surface for the S5 balance-monitoring pilot (gated by the {@code balance-monitoring}
 * feature flag). Returns current gateway float balances, treasury positions, and the latest nightly
 * float snapshots in one view.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/balance-monitoring")
@PreAuthorize("hasRole('ADMIN')")
public class BalanceMonitoringController {

    private final BalanceMonitoringService monitoringService;

    public BalanceMonitoringController(BalanceMonitoringService monitoringService) {
        this.monitoringService = monitoringService;
    }

    @GetMapping(path = "/overview")
    public ResponseEntity<?> overview() {
        try {
            return ResponseEntity.ok(monitoringService.overview());
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "BALANCE_MONITORING_DISABLED", "message", e.getMessage()));
        }
    }
}
