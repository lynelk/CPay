package net.citotech.cito.refund;

import java.math.BigDecimal;

/** A single refund's current state (audit B6). */
public record RefundRecord(
        long id,
        String refundReference,
        long merchantId,
        long originalTransactionId,
        String originalMerchantRef,
        Long payoutTransactionId,
        BigDecimal requestedAmount,
        RefundStatus status,
        String reason,
        String failureMessage) {
}
