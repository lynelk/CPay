package net.citotech.cito.refund;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import net.citotech.cito.Common;
import net.citotech.cito.DoPayGateway;
import net.citotech.cito.Model.GatewayChargeDetails;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Transaction;
import net.citotech.cito.SendMail;
import net.citotech.cito.async.ManagedAsyncTasks;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.merchant.MerchantNotificationPreferenceService;
import net.citotech.cito.money.MoneyAmount;
import org.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Refund lifecycle service (audit B6): a refund is now a first-class, queryable object with an
 * explicit state machine and support for partial refunds (tracked cumulatively against the
 * original payin so the total refunded can never exceed what was collected), replacing the
 * legacy always-full-amount, state-less payout reversal.
 */
@Service
public class RefundService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final DoubleEntryLedgerService ledgerService;
    private final MerchantNotificationPreferenceService notificationPreferenceService;

    public RefundService(NamedParameterJdbcTemplate jdbcTemplate,
                         PlatformTransactionManager transactionManager,
                         DoubleEntryLedgerService ledgerService,
                         MerchantNotificationPreferenceService notificationPreferenceService) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
        this.ledgerService = ledgerService;
        this.notificationPreferenceService = notificationPreferenceService;
    }

    /** amount == null means a full refund of whatever remains unrefunded on the original payin. */
    public RefundRecord requestRefund(Merchant merchant, String originalMerchantRef, String refundReference,
            BigDecimal amount, String reason) {
        // A retried request with the same reference returns the existing refund rather than
        // erroring or double-refunding - refund_reference is unique per merchant.
        Optional<RefundRecord> existing = findByReference(merchant.getId(), refundReference);
        if (existing.isPresent()) {
            return existing.get();
        }

        Transaction originalTx = getSuccessfulPayin(originalMerchantRef, merchant.getId());
        if (originalTx == null) {
            throw new PaymentGatewayException("No successful payin found for reference " + originalMerchantRef);
        }
        BigDecimal originalAmount = MoneyAmount.of(String.valueOf(originalTx.getOriginal_amount())).asBigDecimal();
        BigDecimal alreadyRefunded = refundedSoFar(originalTx.getId());
        BigDecimal remaining = originalAmount.subtract(alreadyRefunded);
        BigDecimal refundAmount = amount == null ? remaining : amount;
        if (refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("Refund amount must be greater than zero");
        }
        if (refundAmount.compareTo(remaining) > 0) {
            throw new PaymentGatewayException(
                "Refund amount " + refundAmount + " exceeds the unrefunded balance " + remaining
                    + " on transaction " + originalMerchantRef);
        }

        long refundId = insertRefund(merchant.getId(), refundReference, originalTx.getId(), originalMerchantRef, refundAmount, reason);
        transition(refundId, RefundStatus.PROCESSING, null);

        try {
            RefundOutcome outcome = executeRefundPayout(merchant, originalTx, refundReference, refundAmount, reason);
            if (outcome.succeeded()) {
                transition(refundId, RefundStatus.COMPLETED, null);
                linkPayoutTransaction(refundId, outcome.payoutTransactionId());
                notifyRefundOutcome(merchant, refundReference, refundAmount, true, null);
            } else {
                transition(refundId, RefundStatus.FAILED, outcome.message());
                notifyRefundOutcome(merchant, refundReference, refundAmount, false, outcome.message());
            }
        } catch (RuntimeException ex) {
            transition(refundId, RefundStatus.FAILED, ex.getMessage());
            notifyRefundOutcome(merchant, refundReference, refundAmount, false, ex.getMessage());
            throw ex;
        }

        return findById(refundId).orElseThrow(() -> new PaymentGatewayException("Failed to read back the refund just created"));
    }

    /**
     * Notifies the merchant of a terminal refund outcome (audit N5) - the refund.completed/
     * refund.failed events in {@link net.citotech.cito.webhook.WebhookEventCatalog}. Previously
     * nothing notified the merchant of a refund outcome at all; this routes through the merchant's
     * own {@link MerchantNotificationPreferenceService} preference rather than sending
     * unconditionally, so an explicit NONE suppresses the send and a configured address overrides
     * the merchant's primary contact. SMS is resolved but not dispatched here since no SMS provider
     * is wired into this codebase yet. A failure in resolving/sending the notification must never
     * affect the refund result already recorded, so it's swallowed rather than propagated.
     */
    private void notifyRefundOutcome(Merchant merchant, String refundReference, BigDecimal amount,
            boolean succeeded, String failureMessage) {
        try {
            String eventType = succeeded ? "refund.completed" : "refund.failed";
            MerchantNotificationPreferenceService.ResolvedNotification notification =
                notificationPreferenceService.resolveChannel(merchant.getId(), eventType);
            if (!notification.shouldSend()
                    || notification.channel() != MerchantNotificationPreferenceService.Channel.EMAIL) {
                return;
            }
            String to = notification.address();
            String subject = succeeded ? "Refund completed: " + refundReference : "Refund failed: " + refundReference;
            String body = succeeded
                ? "Your refund " + refundReference + " for " + amount + " has completed successfully."
                : "Your refund " + refundReference + " for " + amount + " failed."
                    + (failureMessage == null || failureMessage.isEmpty() ? "" : " Reason: " + failureMessage);
            ManagedAsyncTasks.run("refund-notification-" + refundReference,
                () -> new SendMail().sendSimpleMessage(to, subject, body, jdbcTemplate));
        } catch (Exception ignored) {
            // The refund result is already recorded; a notification routing/send failure must not
            // affect it or bubble up to the caller.
        }
    }

    public Optional<RefundRecord> findByReference(long merchantId, String refundReference) {
        List<RefundRecord> rows = jdbcTemplate.query(
            "SELECT * FROM refunds WHERE merchant_id=:merchant_id AND refund_reference=:refund_reference",
            new MapSqlParameterSource().addValue("merchant_id", merchantId).addValue("refund_reference", refundReference),
            this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private Optional<RefundRecord> findById(long id) {
        List<RefundRecord> rows = jdbcTemplate.query(
            "SELECT * FROM refunds WHERE id=:id",
            new MapSqlParameterSource("id", id),
            this::mapRow);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    private RefundRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new RefundRecord(
            rs.getLong("id"),
            rs.getString("refund_reference"),
            rs.getLong("merchant_id"),
            rs.getLong("original_transaction_id"),
            rs.getString("original_merchant_ref"),
            rs.getObject("payout_transaction_id") == null ? null : rs.getLong("payout_transaction_id"),
            rs.getBigDecimal("requested_amount"),
            RefundStatus.valueOf(rs.getString("refund_status")),
            rs.getString("reason"),
            rs.getString("failure_message"));
    }

    private BigDecimal refundedSoFar(long originalTransactionId) {
        BigDecimal sum = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(requested_amount), 0) FROM refunds "
                + "WHERE original_transaction_id=:original_transaction_id "
                + "AND refund_status IN ('REQUESTED','PROCESSING','COMPLETED')",
            new MapSqlParameterSource("original_transaction_id", originalTransactionId),
            BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    private long insertRefund(long merchantId, String refundReference, long originalTransactionId,
            String originalMerchantRef, BigDecimal amount, String reason) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("refund_reference", refundReference);
        p.addValue("merchant_id", merchantId);
        p.addValue("original_transaction_id", originalTransactionId);
        p.addValue("original_merchant_ref", originalMerchantRef);
        p.addValue("requested_amount", amount);
        p.addValue("reason", reason);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(
            "INSERT INTO refunds (refund_reference, merchant_id, original_transaction_id, original_merchant_ref, requested_amount, reason) "
                + "VALUES (:refund_reference, :merchant_id, :original_transaction_id, :original_merchant_ref, :requested_amount, :reason)",
            p, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void transition(long refundId, RefundStatus next, String failureMessage) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", refundId);
        p.addValue("status", next.name());
        p.addValue("failure_message", failureMessage);
        jdbcTemplate.update(
            "UPDATE refunds SET refund_status=:status, failure_message=COALESCE(:failure_message, failure_message) WHERE id=:id",
            p);
    }

    private void linkPayoutTransaction(long refundId, Long payoutTransactionId) {
        if (payoutTransactionId == null) {
            return;
        }
        jdbcTemplate.update(
            "UPDATE refunds SET payout_transaction_id=:payout_transaction_id WHERE id=:id",
            new MapSqlParameterSource().addValue("id", refundId).addValue("payout_transaction_id", payoutTransactionId));
    }

    private RefundOutcome executeRefundPayout(Merchant merchant, Transaction originalTx, String refundReference,
            BigDecimal refundAmount, String reason) {
        String gatewayId = originalTx.getGateway_id();
        GatewayChargeDetails chargeDetails = DoPayGateway.getGatewayChargeDetailsById(jdbcTemplate, gatewayId, merchant.getId());
        Double amountDouble = refundAmount.doubleValue();
        Double charges = chargeDetails == null ? 0.0 : DoPayGateway.getCustomerOutboundCharges(amountDouble, chargeDetails);
        Double txCost = chargeDetails == null ? 0.0 : DoPayGateway.getCostOfOutboundCharges(amountDouble, chargeDetails);

        Transaction refundTx = new Transaction();
        refundTx.setGateway_id(gatewayId);
        refundTx.setOriginal_amount(amountDouble);
        refundTx.setPayer_number(originalTx.getPayer_number());
        refundTx.setStatus("PENDING");
        refundTx.setMerchant_id(merchant.getId() + "");
        refundTx.setTx_description(merchant.getShort_name());
        refundTx.setTx_merchant_description(reason == null ? "Refund" : reason);
        refundTx.setTx_type(Transaction.TX_TYPE_PAYOUT_REVERSAL);
        refundTx.setTx_unique_id(Common.generateUuid());
        refundTx.setTx_merchant_ref(refundReference);
        refundTx.setCallback_url("");
        refundTx.setOriginate_ip("");
        refundTx.setCharging_method(chargeDetails == null ? "" : chargeDetails.getCustomerOutboundChargeMethod());
        refundTx.setCharges(charges == null ? 0.0 : charges);
        refundTx.setTx_cost(txCost == null ? 0.0 : txCost);
        refundTx.setTx_request_trace("");
        refundTx.setTx_update_trace("");
        refundTx.setTx_gateway_ref("");

        // Reserve-then-capture (audit A8, applied consistently here too): this calls
        // Common.doPayOut directly, same as the batch-payout path, so it needs the same
        // ledger-level hold PaymentOrchestrationService.payout() uses for the v2 flow.
        String reservationReference = "refund-reserve:" + merchant.getAccount_number() + ":" + refundReference;
        BigDecimal reservedAmount = refundAmount.add(charges == null ? BigDecimal.ZERO : BigDecimal.valueOf(charges));
        ledgerService.reserve(reservationReference, merchant.getId(), refundReference, reservedAmount,
            originalTx.getCurrency() == null || originalTx.getCurrency().isEmpty() ? "UGX" : originalTx.getCurrency());

        String resultJson;
        try {
            resultJson = Common.doPayOut(refundTx, merchant, jdbcTemplate, transactionManager);
        } catch (RuntimeException ex) {
            ledgerService.releaseReservation(reservationReference);
            return RefundOutcome.failed(ex.getMessage(), null);
        }

        JSONObject result = new JSONObject(resultJson);
        boolean succeeded = "OK".equals(result.optString("state")) && "000".equals(result.optString("code"));
        if (succeeded) {
            ledgerService.captureReservation(reservationReference);
            return RefundOutcome.succeeded(refundTx.getId());
        }
        ledgerService.releaseReservation(reservationReference);
        return RefundOutcome.failed(result.optString("message", resultJson), refundTx.getId());
    }

    private Transaction getSuccessfulPayin(String merchantRef, long merchantId) {
        String sql = "SELECT * FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG
            + " WHERE tx_merchant_ref=:ref AND merchant_id=:mid AND status='SUCCESSFUL' AND tx_type=:type LIMIT 1";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("ref", merchantRef);
        p.addValue("mid", merchantId);
        p.addValue("type", Transaction.TX_TYPE_PAYIN);
        List<Transaction> rows = jdbcTemplate.query(sql, p, Common.getTransactionRowMapper());
        return rows.isEmpty() ? null : rows.get(0);
    }

    private record RefundOutcome(boolean succeeded, String message, Long payoutTransactionId) {
        static RefundOutcome succeeded(long payoutTransactionId) {
            return new RefundOutcome(true, null, payoutTransactionId);
        }

        static RefundOutcome failed(String message, Long payoutTransactionId) {
            return new RefundOutcome(false, message, payoutTransactionId);
        }
    }
}
