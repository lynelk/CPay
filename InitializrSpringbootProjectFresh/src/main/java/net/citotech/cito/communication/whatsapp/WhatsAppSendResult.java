package net.citotech.cito.communication.whatsapp;

/** Normalized WhatsApp provider outcome; raw provider detail remains internal/audit-only. */
public record WhatsAppSendResult(String status, String providerTrace, String responseBody) {
    public static WhatsAppSendResult sent(String trace, String body) {
        return new WhatsAppSendResult("SENT", trace, body);
    }

    public static WhatsAppSendResult rejected(String trace, String body) {
        return new WhatsAppSendResult("REJECTED", trace, body);
    }

    public static WhatsAppSendResult failed(String trace, String body) {
        return new WhatsAppSendResult("FAILED", trace, body);
    }

    public boolean successful() {
        return "SENT".equals(status);
    }
}
