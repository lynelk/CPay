package net.citotech.cito.crossborder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.crossborder.TreasuryPositionService.TreasuryPositionRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ops/finance surface for the treasury positions maintained by the cross-border and payout
 * reservation paths (V12). Previously finance could only see available vs reserved balances by
 * querying the database directly; this exposes the same view over the admin API, including the
 * computed net-available balance.
 */
@RestController
@RequestMapping(path = "/api/v2/admin/treasury")
@PreAuthorize("hasRole('ADMIN')")
public class TreasuryPositionController {
    private final TreasuryPositionService treasuryPositionService;

    public TreasuryPositionController(TreasuryPositionService treasuryPositionService) {
        this.treasuryPositionService = treasuryPositionService;
    }

    @GetMapping(path = "/positions")
    public ResponseEntity<?> listPositions() {
        List<TreasuryPositionRow> rows = treasuryPositionService.listPositions();
        return ResponseEntity.ok(rows.stream().map(TreasuryPositionController::toView).toList());
    }

    @GetMapping(path = "/positions/{currency}")
    public ResponseEntity<?> positionByCurrency(@PathVariable("currency") String currency) {
        try {
            TreasuryPositionRow row = treasuryPositionService.findByCurrency(currency);
            if (row == null) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok(toView(row));
        } catch (PaymentGatewayException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "INVALID_CURRENCY", "message", e.getMessage()));
        }
    }

    private static Map<String, Object> toView(TreasuryPositionRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("currency", row.currency());
        view.put("availableBalance", row.availableBalance());
        view.put("reservedBalance", row.reservedBalance());
        view.put("netAvailable", row.netAvailable());
        view.put("status", row.status());
        view.put("updatedAt", row.updatedAt());
        return view;
    }
}
