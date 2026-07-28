package net.citotech.cito.api.v2;

import jakarta.servlet.http.HttpServletRequest;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.batch.BatchPayoutStatusService;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch payout status aggregation + idempotent partial retry (audit B8): the legacy batch payout
 * loop had no way to see aggregate progress or to re-run just the failed rows of a batch.
 */
@RestController
@RequestMapping(path = "/api/v2/batch-payouts")
public class BatchPayoutsV2Controller {
    private final BatchPayoutStatusService batchPayoutStatusService;
    private final V2RequestSecurityService securityService;

    public BatchPayoutsV2Controller(BatchPayoutStatusService batchPayoutStatusService,
                                    V2RequestSecurityService securityService) {
        this.batchPayoutStatusService = batchPayoutStatusService;
        this.securityService = securityService;
    }

    @GetMapping(path = "/{batchId}/status")
    public ResponseEntity<?> status(@PathVariable long batchId,
                                    @RequestParam("merchantNumber") String merchantNumber,
                                    HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            return ResponseEntity.ok(batchPayoutStatusService.status(batchId, merchant.getId()));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
        }
    }

    @PostMapping(path = "/{batchId}/retry-failed")
    public ResponseEntity<?> retryFailed(@PathVariable long batchId,
                                         @RequestParam("merchantNumber") String merchantNumber,
                                         HttpServletRequest servletRequest) {
        try {
            Merchant merchant = securityService.verify(servletRequest, "", merchantNumber);
            int retried = batchPayoutStatusService.retryFailed(batchId, merchant.getId());
            return ResponseEntity.ok(java.util.Map.of("code", "000", "retriedCount", retried));
        } catch (V2RequestSecurityException e) {
            return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", e.getMessage());
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.NOT_FOUND, "BATCH_NOT_FOUND", e.getMessage());
        }
    }

    private ResponseEntity<?> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(java.util.Map.of("code", code, "message", message == null ? "" : message));
    }
}
