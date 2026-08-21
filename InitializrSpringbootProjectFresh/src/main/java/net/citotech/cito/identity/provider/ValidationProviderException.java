package net.citotech.cito.identity.provider;

/**
 * Technical provider failure (ISO domain mapping: identity/provider). Carries a stable CPay
 * normalized code (e.g. {@code PROVIDER_AUTHENTICATION_ERROR}) and never embeds provider secrets,
 * access tokens, raw NINs, or full sensitive payloads in the message.
 */
public class ValidationProviderException extends RuntimeException {

    private final String providerCode;
    private final String normalizedCode;

    public ValidationProviderException(
            String providerCode, String normalizedCode, String safeMessage) {
        super(safeMessage);
        this.providerCode = providerCode;
        this.normalizedCode = normalizedCode;
    }

    public String providerCode() {
        return providerCode;
    }

    public String normalizedCode() {
        return normalizedCode;
    }
}
