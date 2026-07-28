package net.citotech.cito.Model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AirtelMoneyOpenApiPaymentGatewayTest {

    @Test
    void defaultEndpointConfigurationUsesAirtelV2Routes() {
        AirtelMoneyOpenApiPaymentGateway gateway = new AirtelMoneyOpenApiPaymentGateway();
        gateway.setApiDetails("https://openapiuat.airtel.africa", "client-id", "client-secret", "1234");

        assertThat(gateway.tokenUrl()).isEqualTo("https://openapiuat.airtel.africa/auth/oauth2/token");
        assertThat(gateway.collectionUrl()).isEqualTo("https://openapiuat.airtel.africa/merchant/v2/payments/");
        assertThat(gateway.disbursementUrl()).isEqualTo("https://openapiuat.airtel.africa/standard/v2/disbursements/");
        assertThat(gateway.balanceUrl()).isEqualTo("https://openapiuat.airtel.africa/standard/v2/users/balance");
        assertThat(gateway.statusUrl("collection", "CPAY-001"))
            .isEqualTo("https://openapiuat.airtel.africa/standard/v2/payments/CPAY-001/");
        assertThat(gateway.statusUrl("disbursement", "CPAY-002"))
            .isEqualTo("https://openapiuat.airtel.africa/standard/v2/disbursements/CPAY-002/");
    }

    @Test
    void endpointConfigurationAcceptsRelativeAndAbsoluteOverrides() {
        AirtelMoneyOpenApiPaymentGateway gateway = new AirtelMoneyOpenApiPaymentGateway();
        gateway.setApiDetails("https://example.airtel.test/root/", "client-id", "client-secret", "1234");
        gateway.setEndpointDetails(
            "/oauth/token",
            "/collections",
            "https://payments.airtel.test/payouts",
            "/wallet/balance",
            "/collections/{reference}/status",
            "https://payments.airtel.test/payouts/{id}/status");

        assertThat(gateway.tokenUrl()).isEqualTo("https://example.airtel.test/root/oauth/token");
        assertThat(gateway.collectionUrl()).isEqualTo("https://example.airtel.test/root/collections");
        assertThat(gateway.disbursementUrl()).isEqualTo("https://payments.airtel.test/payouts");
        assertThat(gateway.balanceUrl()).isEqualTo("https://example.airtel.test/root/wallet/balance");
        assertThat(gateway.statusUrl("collection", "REF 1"))
            .isEqualTo("https://example.airtel.test/root/collections/REF%201/status");
        assertThat(gateway.statusUrl("disbursement", "REF/2"))
            .isEqualTo("https://payments.airtel.test/payouts/REF%2F2/status");
    }

    @Test
    void blankEndpointOverridesKeepTheCurrentDefaults() {
        AirtelMoneyOpenApiPaymentGateway gateway = new AirtelMoneyOpenApiPaymentGateway();
        gateway.setApiDetails("", "client-id", "client-secret", "1234");
        gateway.setEndpointDetails("", null, "  ", "", null, "");

        assertThat(gateway.collectionUrl()).isEqualTo("https://openapiuat.airtel.africa/merchant/v2/payments/");
        assertThat(gateway.disbursementUrl()).isEqualTo("https://openapiuat.airtel.africa/standard/v2/disbursements/");
    }
}
