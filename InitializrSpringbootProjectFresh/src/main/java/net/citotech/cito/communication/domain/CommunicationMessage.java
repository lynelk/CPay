package net.citotech.cito.communication.domain;

import java.time.Instant;

/**
 * Parent logical communication row (ISO domain mapping: communication/domain). One merchant
 * request maps to one {@code communication_messages} row; each provider/channel delivery attempt
 * is a separate {@link CommunicationAttempt}. Keeping the intent separate from attempts makes
 * idempotency and failover reporting unambiguous.
 */
public record CommunicationMessage(
        long id,
        String publicId,
        long merchantId,
        String externalReference,
        String idempotencyKey,
        CommunicationPurpose purpose,
        String recipient,
        CommunicationChannel selectedChannel,
        String selectedProviderCode,
        String templateKey,
        boolean fallbackEnabled,
        CommunicationStatus status,
        Instant scheduledAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt) {}
