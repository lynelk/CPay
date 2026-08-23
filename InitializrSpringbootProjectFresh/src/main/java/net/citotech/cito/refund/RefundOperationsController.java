package net.citotech.cito.refund;

import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/admin/refund-operations")
@PreAuthorize("hasRole('ADMIN')")
public class RefundOperationsController {
    private final RefundService refundService;
    private final PaymentDisputeService disputeService;

    public RefundOperationsController(
            RefundService refundService, PaymentDisputeService disputeService) {
        this.refundService = refundService;
        this.disputeService = disputeService;
    }

    @PostMapping("/refunds/{merchantId}/{reference}/approve")
    public ResponseEntity<?> approve(
            @PathVariable long merchantId,
            @PathVariable String reference,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    refundService.approveRefund(merchantId, reference, text(body.get("approver"))));
        } catch (PaymentGatewayException e) {
            return bad("REFUND_APPROVAL_REJECTED", e);
        }
    }

    @PostMapping("/refunds/{merchantId}/{reference}/reject")
    public ResponseEntity<?> reject(
            @PathVariable long merchantId,
            @PathVariable String reference,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    refundService.rejectRefund(
                            merchantId,
                            reference,
                            text(body.get("reviewer")),
                            text(body.get("reason"))));
        } catch (PaymentGatewayException e) {
            return bad("REFUND_REJECTION_REJECTED", e);
        }
    }

    @GetMapping("/refunds/{merchantId}/{reference}/attempts")
    public ResponseEntity<?> attempts(
            @PathVariable long merchantId, @PathVariable String reference) {
        try {
            return ResponseEntity.ok(refundService.attempts(merchantId, reference));
        } catch (PaymentGatewayException e) {
            return bad("REFUND_NOT_FOUND", e);
        }
    }

    @GetMapping("/timeline")
    public ResponseEntity<?> timeline(
            @RequestParam long merchantId,
            @RequestParam String transactionReference,
            @RequestParam(value = "limit", defaultValue = "100") int limit) {
        return ResponseEntity.ok(
                refundService.financialTimeline(merchantId, transactionReference, limit));
    }

    @PostMapping("/reversals")
    public ResponseEntity<?> recordReversal(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    disputeService.recordReversal(
                            longValue(body.get("merchantId")),
                            longValue(body.get("originalTransactionId")),
                            text(body.get("originalMerchantRef")),
                            text(body.get("providerChannel")),
                            text(body.get("providerReference")),
                            decimal(body.get("amount")),
                            text(body.get("currencyCode")),
                            text(body.get("reversalType")),
                            text(body.get("reasonCode")),
                            text(body.get("evidenceJson"))));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("code", "REVERSAL_REJECTED", "message", e.getMessage()));
        }
    }

    @PostMapping("/disputes/{merchantId}/{reference}/status")
    public ResponseEntity<?> updateDisputeStatus(
            @PathVariable long merchantId,
            @PathVariable String reference,
            @RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(
                    disputeService.updateStatus(
                            merchantId,
                            reference,
                            text(body.get("status")),
                            text(body.get("actor")),
                            text(body.get("notes"))));
        } catch (PaymentGatewayException e) {
            return bad("DISPUTE_UPDATE_REJECTED", e);
        }
    }

    private ResponseEntity<?> bad(String code, PaymentGatewayException e) {
        return ResponseEntity.badRequest().body(Map.of("code", code, "message", e.getMessage()));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(text(value));
    }

    private BigDecimal decimal(Object value) {
        return new BigDecimal(text(value));
    }
}