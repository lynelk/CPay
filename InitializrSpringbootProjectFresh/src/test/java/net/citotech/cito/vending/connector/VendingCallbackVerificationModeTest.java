package net.citotech.cito.vending.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingCallbackVerificationModeTest {

    @Test
    void enumeratesAllSupportedCallbackVerificationModes() {
        assertThat(VendingCallbackVerificationMode.values())
                .containsExactly(
                        VendingCallbackVerificationMode.HMAC_SHA256_TS_NONCE_BODY,
                        VendingCallbackVerificationMode.HMAC_SHA256_TS_BODY,
                        VendingCallbackVerificationMode.HMAC_SHA256_BODY,
                        VendingCallbackVerificationMode.STATIC_TOKEN_HEADER,
                        VendingCallbackVerificationMode.VERIFY_BY_PROVIDER_QUERY);
    }

    @Test
    void requireAcceptsProviderQueryMode() {
        assertThat(VendingCallbackVerificationMode.require("VERIFY_BY_PROVIDER_QUERY"))
                .isEqualTo(VendingCallbackVerificationMode.VERIFY_BY_PROVIDER_QUERY);
        assertThat(VendingCallbackVerificationMode.require("STATIC_TOKEN_HEADER"))
                .isEqualTo(VendingCallbackVerificationMode.STATIC_TOKEN_HEADER);
    }

    @Test
    void requireRejectsUnknownMode() {
        assertThatThrownBy(() -> VendingCallbackVerificationMode.require("BASIC_AUTH"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported vending callback verification mode");
    }
}
