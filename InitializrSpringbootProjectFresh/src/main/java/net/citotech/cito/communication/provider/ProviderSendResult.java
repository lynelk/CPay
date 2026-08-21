package net.citotech.cito.communication.provider;

/**
 * Normalized outcome of one provider send (ISO domain mapping: communication/provider). The
 * provider adapter must never place credentials, tokens, full raw PII, or an unrestricted provider
 * response in {@code safeResponse} - that field is audit/diagnostics only.
 */
public record ProviderSendResult(
        Status status,
        String providerMessageId,
        String providerCode,
        String normalizedCode,
        String trace,
        String safeResponse,
        boolean retryable) {

    public enum Status {
        ACCEPTED,
        SENT,
        DELIVERED,
        REJECTED,
        FAILED,
        UNKNOWN
    }

    public static ProviderSendResult accepted(
            String providerCode, String providerMessageId, String normalizedCode) {
        return new ProviderSendResult(
                Status.ACCEPTED, providerMessageId, providerCode, normalizedCode, "", "", false);
    }

    public static ProviderSendResult sent(
            String providerCode, String providerMessageId, String normalizedCode) {
        return new ProviderSendResult(
                Status.SENT, providerMessageId, providerCode, normalizedCode, "", "", false);
    }

    public static ProviderSendResult rejected(
            String providerCode, String normalizedCode, String trace, String safeResponse) {
        return new ProviderSendResult(
                Status.REJECTED, null, providerCode, normalizedCode, trace, safeResponse, false);
    }

    public static ProviderSendResult failed(
            String providerCode,
            String normalizedCode,
            String trace,
            String safeResponse,
            boolean retryable) {
        return new ProviderSendResult(
                Status.FAILED, null, providerCode, normalizedCode, trace, safeResponse, retryable);
    }

    public static ProviderSendResult unknown(
            String providerCode, String trace, String safeResponse) {
        return new ProviderSendResult(
                Status.UNKNOWN, null, providerCode, "UNKNOWN", trace, safeResponse, false);
    }

    /** Provider accepted or sent evidence exists; the merchant communication is billable. */
    public boolean isBillable() {
        return status == Status.ACCEPTED || status == Status.SENT || status == Status.DELIVERED;
    }
}
