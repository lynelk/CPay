package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingCallbackVerificationModeTest {

    @Test
    void requireReturnsValidEnum() {
        assertEquals(
                VendingCallbackVerificationMode.HMAC_SHA256_TS_NONCE_BODY,
                VendingCallbackVerificationMode.require("HMAC_SHA256_TS_NONCE_BODY"));
    }

    @Test
    void requireNormalizesCase() {
        assertEquals(
                VendingCallbackVerificationMode.HMAC_SHA256_BODY,
                VendingCallbackVerificationMode.require("hmac_sha256_body"));
    }

    @Test
    void requireAllModesExist() {
        assertEquals(5, VendingCallbackVerificationMode.values().length);
    }

    @Test
    void requireIncludesVerifyByProviderQuery() {
        assertEquals(
                VendingCallbackVerificationMode.VERIFY_BY_PROVIDER_QUERY,
                VendingCallbackVerificationMode.require("VERIFY_BY_PROVIDER_QUERY"));
    }

    @Test
    void requireRejectsNull() {
        assertThrows(
                PaymentGatewayException.class,
                () -> VendingCallbackVerificationMode.require(null));
    }

    @Test
    void requireRejectsBlank() {
        assertThrows(
                PaymentGatewayException.class,
                () -> VendingCallbackVerificationMode.require("   "));
    }

    @Test
    void requireRejectsUnknownMode() {
        assertThrows(
                PaymentGatewayException.class,
                () -> VendingCallbackVerificationMode.require("INVALID_MODE"));
    }

    @Test
    void valueReturnsName() {
        assertEquals(
                "STATIC_TOKEN_HEADER",
                VendingCallbackVerificationMode.STATIC_TOKEN_HEADER.value());
    }
}
