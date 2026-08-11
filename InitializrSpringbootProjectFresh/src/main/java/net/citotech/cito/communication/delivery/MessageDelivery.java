package net.citotech.cito.communication.delivery;

import java.math.BigDecimal;

/**
 * A row from {@code communication_message_deliveries} (V53, track B5a) — the channel-agnostic
 * per-message delivery ledger. It generalizes the legacy {@code merchant_sms} status/charge/trace
 * shape so SMS, EMAIL, WHATSAPP and USSD deliveries share one log and one billing meter pipeline.
 * {@code referenceType}/{@code referenceId} link the row back to its source artifact (a campaign
 * item, a WhatsApp message, a USSD session) without a polymorphic FK column.
 */
public record MessageDelivery(
        long id,
        long merchantId,
        String channel,
        String providerCode,
        String referenceType,
        Long referenceId,
        String recipient,
        DeliveryStatus status,
        String trace,
        String gwResponse,
        BigDecimal chargedAmount,
        boolean billed) {}
