package net.citotech.cito.communication.whatsapp;

/** Provider-neutral WhatsApp send request. */
public record WhatsAppSendRequest(long merchantId, String recipients, String content) {
    public WhatsAppSendRequest {
        if (merchantId <= 0) {
            throw new IllegalArgumentException("merchantId must be positive");
        }
        if (recipients == null || recipients.isBlank()) {
            throw new IllegalArgumentException("recipients are required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
