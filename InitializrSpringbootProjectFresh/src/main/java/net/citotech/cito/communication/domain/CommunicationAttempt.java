package net.citotech.cito.communication.domain;

import java.time.Instant;
import net.citotech.cito.communication.delivery.DeliveryStatus;

/**
 * One delivery attempt of a logical communication (ISO domain mapping: communication/domain).
 * Mirrors the {@code communication_message_deliveries} row's attempt semantics: a parent message
 * can fail over across providers/channels, and each attempt gets its own row ordered by
 * {@code attemptNo}.
 */
public record CommunicationAttempt(
        long id,
        long communicationId,
        int attemptNo,
        CommunicationChannel channel,
        String providerCode,
        String providerMessageId,
        String recipient,
        DeliveryStatus status,
        String failureCode,
        boolean retryable,
        String trace,
        String gwResponse,
        Instant sentAt,
        Instant deliveredAt,
        Instant createdAt) {}
