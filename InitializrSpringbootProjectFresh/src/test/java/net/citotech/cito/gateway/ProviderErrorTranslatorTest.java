package net.citotech.cito.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import net.citotech.cito.ErrorCatalog.Category;
import net.citotech.cito.gateway.ProviderErrorTranslator.Translation;
import org.junit.jupiter.api.Test;

/**
 * Covers audit C6/J7: every raw provider response shape a real integration must survive (declined,
 * unavailable/retryable, malformed, unclassified) must translate to a merchant-safe message that
 * never echoes the raw provider text, plus internal-exception cases must get a stable, non-sensitive
 * reason code rather than the raw exception message.
 */
class ProviderErrorTranslatorTest {

    @Test
    void aNoResponseAtAllIsRetryableProviderUnavailable() {
        Translation translation = ProviderErrorTranslator.translateProviderResponse(0, null);

        assertThat(translation.stableCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(translation.category()).isEqualTo(Category.PROVIDER);
        assertThat(translation.retryable()).isTrue();
        assertThat(translation.merchantMessage()).doesNotContain("null");
    }

    @Test
    void a401Or403IsAuthInvalidNotRetryable() {
        assertThat(ProviderErrorTranslator.translateProviderResponse(401, "{\"raw\":\"secret internal detail\"}").stableCode())
            .isEqualTo("AUTH_CREDENTIALS_INVALID");
        Translation translation = ProviderErrorTranslator.translateProviderResponse(403, "forbidden");
        assertThat(translation.retryable()).isFalse();
        assertThat(translation.merchantMessage()).doesNotContain("forbidden");
    }

    @Test
    void a408Or429Or5xxIsRetryableProviderUnavailable() {
        assertThat(ProviderErrorTranslator.translateProviderResponse(408, "timeout").stableCode()).isEqualTo("PROVIDER_TIMEOUT");
        assertThat(ProviderErrorTranslator.translateProviderResponse(429, "rate limited").retryable()).isTrue();
        assertThat(ProviderErrorTranslator.translateProviderResponse(503, "unavailable").stableCode()).isEqualTo("PROVIDER_TIMEOUT");
    }

    @Test
    void aGeneric4xxIsProviderDeclinedAndNeverLeaksTheRawBody() {
        String rawBody = "{\"error\":\"insufficient_balance_on_provider_side_account_12345\"}";

        Translation translation = ProviderErrorTranslator.translateProviderResponse(400, rawBody);

        assertThat(translation.stableCode()).isEqualTo("PROVIDER_DECLINED");
        assertThat(translation.retryable()).isFalse();
        assertThat(translation.merchantMessage()).doesNotContain(rawBody).doesNotContain("insufficient_balance_on_provider_side_account_12345");
    }

    @Test
    void a2xxWithABusinessDeclineSignalIsProviderDeclined() {
        // Caller only reaches this path once it has independently classified the transaction as a
        // business decline (e.g. Airtel's status.result_code) despite the HTTP call succeeding.
        Translation translation = ProviderErrorTranslator.translateProviderResponse(200, "{\"result_code\":\"ESB000010\"}", "ESB000010");

        assertThat(translation.stableCode()).isEqualTo("PROVIDER_DECLINED");
    }

    @Test
    void a2xxWithNoUsableSignalAtAllFallsBackToUnclassifiedRetryable() {
        Translation translation = ProviderErrorTranslator.translateProviderResponse(200, "", null);

        assertThat(translation.retryable()).isTrue();
        assertThat(translation.merchantMessage()).contains("could not confirm the outcome");
    }

    @Test
    void signatureVerificationFailedIsItsOwnDistinctNonRetryableAuthenticationCategoryTranslation() {
        Translation translation = ProviderErrorTranslator.SIGNATURE_VERIFICATION_FAILED;

        assertThat(translation.category()).isEqualTo(Category.AUTHENTICATION);
        assertThat(translation.retryable()).isFalse();
        assertThat(translation.stableCode()).isNotEqualTo("PROVIDER_DECLINED");
    }

    @Test
    void internalIoExceptionIsClassifiedAsARetryableProviderConnectionError() {
        Translation translation = ProviderErrorTranslator.translateInternalFailure(new java.io.IOException("connection reset by peer"));

        assertThat(translation.category()).isEqualTo(Category.PROVIDER);
        assertThat(translation.retryable()).isTrue();
        assertThat(translation.merchantMessage()).doesNotContain("connection reset by peer");
    }

    @Test
    void internalJsonExceptionGetsAStableSystemReasonCodeNeverTheRawMessage() {
        String reasonCode = ProviderErrorTranslator.internalReasonCode(
            new org.json.JSONException("Unterminated string at character 42 of {\"secret_field\":\"leak"));

        assertThat(reasonCode).isEqualTo("SYSTEM_JSON_PARSE_ERROR");
    }

    @Test
    void internalCryptoExceptionGetsAStableSystemReasonCode() {
        assertThat(ProviderErrorTranslator.internalReasonCode(new java.security.GeneralSecurityException("bad key")))
            .isEqualTo("SYSTEM_CRYPTO_ERROR");
    }

    @Test
    void unknownInternalExceptionFallsBackToAGenericSystemReasonCode() {
        assertThat(ProviderErrorTranslator.internalReasonCode(new RuntimeException("anything")))
            .isEqualTo("SYSTEM_UNKNOWN_ERROR");
        assertThat(ProviderErrorTranslator.internalReasonCode(null)).isEqualTo("SYSTEM_UNKNOWN_ERROR");
    }

    @Test
    void translateInternalFailureNeverIncludesTheCausesRawMessage() {
        Translation translation = ProviderErrorTranslator.translateInternalFailure(
            new RuntimeException("internal database password is hunter2"));

        assertThat(translation.merchantMessage()).doesNotContain("hunter2");
        assertThat(translation.category()).isEqualTo(Category.SYSTEM);
        assertThat(translation.retryable()).isTrue();
    }
}
