package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import net.citotech.cito.Model.GateWayResponse;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * Covers audit D3: legacy numeric error codes now carry stable machine-readable metadata (category,
 * retryable, docs URL) matching the shape documented in Docs/Error-catalog.md.
 */
class ErrorCatalogTest {

    @Test
    void knownCodesResolveToTheDocumentedCategoryAndRetryFlag() {
        assertThat(ErrorCatalog.lookup("111").stableCode()).isEqualTo("PAYMENT_INSUFFICIENT_FUNDS");
        assertThat(ErrorCatalog.lookup("111").category())
                .isEqualTo(ErrorCatalog.Category.BUSINESS_RULE);
        assertThat(ErrorCatalog.lookup("111").retryable()).isFalse();

        assertThat(ErrorCatalog.lookup("102").stableCode()).isEqualTo("SYSTEM_UNAVAILABLE");
        assertThat(ErrorCatalog.lookup("102").retryable()).isTrue();

        assertThat(ErrorCatalog.lookup("145").stableCode()).isEqualTo("RATE_LIMITED");
        assertThat(ErrorCatalog.lookup("145").retryable()).isTrue();
    }

    @Test
    void unknownCodesFallBackToARetryableSystemError() {
        ErrorCatalog.Entry entry = ErrorCatalog.lookup("999999");
        assertThat(entry.stableCode()).isEqualTo("SYSTEM_UNAVAILABLE");
        assertThat(entry.retryable()).isTrue();
    }

    @Test
    void docsUrlPointsAtTheCatalogAnchorForThatCode() {
        assertThat(ErrorCatalog.lookup("111").docsUrl())
                .isEqualTo(
                        "https://github.com/lynelk/CPay/blob/main/Docs/Error-catalog.md#payment_insufficient_funds");
    }

    @Test
    void getErrorIncludesCatalogFieldsWithoutBreakingExistingKeys() throws Exception {
        String response = GeneralException.getError("111", "Insufficient funds");
        JSONObject json = new JSONObject(response);

        assertThat(json.getString("state")).isEqualTo("ERROR");
        assertThat(json.getString("code")).isEqualTo("111");
        assertThat(json.getString("message")).isEqualTo("Insufficient funds");
        assertThat(json.getString("error_code")).isEqualTo("PAYMENT_INSUFFICIENT_FUNDS");
        assertThat(json.getString("category")).isEqualTo("business_rule");
        assertThat(json.getBoolean("retryable")).isFalse();
        assertThat(json.getString("docs_url"))
                .contains("Error-catalog.md#payment_insufficient_funds");
    }

    @Test
    void getApiTxMessageIncludesCatalogFieldsWithoutBreakingTransactionDetails() {
        GateWayResponse gatewayResponse = new GateWayResponse();
        gatewayResponse.setStatus("ERROR");
        gatewayResponse.setTransactionStatus("FAILED");
        gatewayResponse.setNetworkId("network-1");
        gatewayResponse.setMessage("declined");

        String response =
                GeneralException.getApiTxMessage("143", "Provider declined", gatewayResponse);
        JSONObject json = new JSONObject(response);

        assertThat(json.getString("state")).isEqualTo("ERROR");
        assertThat(json.getString("code")).isEqualTo("143");
        assertThat(json.getString("error_code")).isEqualTo("PROVIDER_DECLINED");
        assertThat(json.getString("category")).isEqualTo("provider");
        assertThat(json.getBoolean("retryable")).isFalse();
        assertThat(json.getString("docs_url")).contains("Error-catalog.md#provider_declined");
        assertThat(json.getJSONObject("txDetails").getString("networkRef")).isEqualTo("network-1");
    }

    @Test
    void getErrorIncludesTheRequestIdWhenSetOnTheCorrelationMdc() throws Exception {
        MDC.put("request_id", "test-request-id-123");
        try {
            JSONObject json = new JSONObject(GeneralException.getError("102", "boom"));
            assertThat(json.getString("request_id")).isEqualTo("test-request-id-123");
        } finally {
            MDC.remove("request_id");
        }
    }
}
