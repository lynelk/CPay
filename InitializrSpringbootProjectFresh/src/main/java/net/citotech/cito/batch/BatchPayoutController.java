package net.citotech.cito.batch;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Audit B8: merchant-visible batch-level status aggregation and idempotent partial-batch retry.
 * The batch payout loop previously only inspected a beneficiary's most-recent transaction and had
 * no way to see a batch summary or re-run just the failed rows; {@link BatchPayoutStatusService}
 * provides both, and this controller exposes them to the logged-in merchant portal (session-scoped
 * to the merchant's own batches so one merchant can never read or retry another's).
 */
@RestController
@RequestMapping(path = "/api/v2/merchant-self-service/batches")
public class BatchPayoutController {
    private final BatchPayoutStatusService batchStatusService;

    public BatchPayoutController(BatchPayoutStatusService batchStatusService) {
        this.batchStatusService = batchStatusService;
    }

    @GetMapping(path = "/{batchId}")
    public ResponseEntity<?> status(@PathVariable("batchId") long batchId,
                                    HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            return ResponseEntity.ok(batchStatusService.status(batchId, merchantId));
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.UNAUTHORIZED, "MERCHANT_SESSION_REQUIRED", e.getMessage());
        }
    }

    @PostMapping(path = "/{batchId}/retry-failed")
    public ResponseEntity<?> retryFailed(@PathVariable("batchId") long batchId,
                                         HttpServletRequest request) {
        try {
            long merchantId = requireMerchantId(currentMerchantUser(request));
            int retried = batchStatusService.retryFailed(batchId, merchantId);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("code", "000");
            result.put("retried", retried);
            return ResponseEntity.ok(result);
        } catch (PaymentGatewayException e) {
            return error(HttpStatus.BAD_REQUEST, "BATCH_RETRY_REJECTED", e.getMessage());
        }
    }

    private MerchantUser currentMerchantUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("merchantUser") == null) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return (MerchantUser) session.getAttribute("merchantUser");
    }

    private long requireMerchantId(MerchantUser user) {
        if (user == null || user.getMerchant_id() == null) {
            throw new PaymentGatewayException("Merchant login is required");
        }
        return user.getMerchant_id();
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.status(status).body(error);
    }
}
