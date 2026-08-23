package net.citotech.cito.refund;

import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.marketplace.MarketplaceRefundAllocationService;
import net.citotech.cito.platform.MerchantSessionContext;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Session-authenticated refund operations for the Cito merchant workspace. */
@RestController
@RequestMapping("/api/v2/merchant-self-service/refunds")
public class MerchantRefundController {
    private final RefundService refundService;
    private final MarketplaceRefundAllocationService refundAllocationService;
    private final MerchantSessionContext sessionContext;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MerchantRefundController(
            RefundService refundService,
            MarketplaceRefundAllocationService refundAllocationService,
            MerchantSessionContext sessionContext,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.refundService = refundService;
        this.refundAllocationService = refundAllocationService;
        this.sessionContext = sessionContext;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ResponseEntity<?> requestRefund(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try {
            long merchantId = sessionContext.requireMerchantId(request);
            Merchant merchant = Common.getMerchantById(String.valueOf(merchantId), jdbcTemplate);
            if (merchant == null) {
                throw new PaymentGatewayException("Merchant account was not found");
            }
            BigDecimal amount = nullableDecimal(body.get("amount"));
            return ResponseEntity.accepted()
                    .body(
                            refundService.requestRefund(
                                    merchant,
                                    text(body.get("originalReference")),
                                    text(body.get("reference")),
                                    amount,
                                    text(body.get("reason"))));
        } catch (PaymentGatewayException | NumberFormatException e) {
            return bad("REFUND_REJECTED", e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> refunds(
            @RequestParam(value = "limit", defaultValue = "50") int limit,
            HttpServletRequest request) {
        long merchantId = sessionContext.requireMerchantId(request);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return ResponseEntity.ok(
                jdbcTemplate.queryForList(
                        "SELECT refund_reference AS refundReference, original_merchant_ref AS originalReference, "
                                + "requested_amount AS requestedAmount, refund_status AS status, reason, failure_message AS failureMessage, "
                                + "approval_required AS approvalRequired, approval_status AS approvalStatus, requested_by AS requestedBy, "
                                + "approved_by AS approvedBy, approved_at AS approvedAt, split_execution_reference AS splitExecutionReference, "
                                + "created_at AS createdAt FROM refunds WHERE merchant_id=:merchant_id ORDER BY id DESC LIMIT "
                                + safeLimit,
                        new MapSqlParameterSource("merchant_id", merchantId)));
    }

    @GetMapping("/timeline")
    public ResponseEntity<?> timeline(
            @RequestParam("transactionReference") String transactionReference,
            @RequestParam(value = "limit", defaultValue = "100") int limit,
            HttpServletRequest request) {
        long merchantId = sessionContext.requireMerchantId(request);
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return ResponseEntity.ok(
                jdbcTemplate.queryForList(
                        "SELECT event_reference AS eventReference, event_type AS eventType, event_status AS status, "
                                + "amount, currency_code AS currencyCode, detail_json AS detail, created_at AS createdAt "
                                + "FROM payment_financial_timeline WHERE merchant_id=:merchant_id "
                                + "AND transaction_reference=:transaction_reference ORDER BY id DESC LIMIT "
                                + safeLimit,
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("transaction_reference", text(transactionReference))));
    }

    @GetMapping("/split-allocations")
    public ResponseEntity<?> splitAllocations(
            @RequestParam("refundReference") String refundReference,
            HttpServletRequest request) {
        return ResponseEntity.ok(
                refundAllocationService.allocations(
                        sessionContext.requireMerchantId(request), refundReference));
    }

    private BigDecimal nullableDecimal(Object value) {
        String raw = text(value);
        return raw.isEmpty() ? null : new BigDecimal(raw);
    }

    private ResponseEntity<?> bad(String code, String message) {
        return ResponseEntity.badRequest()
                .body(Map.of("code", code, "message", message == null ? "" : message));
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
