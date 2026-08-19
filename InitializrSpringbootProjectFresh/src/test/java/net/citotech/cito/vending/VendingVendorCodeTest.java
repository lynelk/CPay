package net.citotech.cito.vending;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingVendorCodeTest {

    @Test
    void requireReturnsValidEnum() {
        assertEquals(VendingVendorCode.CHARGENOW, VendingVendorCode.require("CHARGENOW"));
    }

    @Test
    void requireNormalizesCase() {
        assertEquals(VendingVendorCode.CHARGENOW, VendingVendorCode.require("chargenow"));
    }

    @Test
    void requireRejectsNull() {
        assertThrows(PaymentGatewayException.class, () -> VendingVendorCode.require(null));
    }

    @Test
    void requireRejectsBlank() {
        assertThrows(PaymentGatewayException.class, () -> VendingVendorCode.require("   "));
    }

    @Test
    void requireRejectsUnknownVendor() {
        assertThrows(PaymentGatewayException.class, () -> VendingVendorCode.require("UNKNOWN"));
    }

    @Test
    void valueReturnsName() {
        assertEquals("CHARGENOW", VendingVendorCode.CHARGENOW.value());
    }
}
