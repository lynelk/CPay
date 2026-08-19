package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingEventTypeTest {

    @Test
    void requireReturnsValidEnum() {
        assertEquals(VendingEventType.DEVICE_ONLINE, VendingEventType.require("DEVICE_ONLINE"));
    }

    @Test
    void requireReturnsUnknownForNull() {
        assertEquals(VendingEventType.UNKNOWN, VendingEventType.require(null));
    }

    @Test
    void requireReturnsUnknownForBlank() {
        assertEquals(VendingEventType.UNKNOWN, VendingEventType.require(""));
    }

    @Test
    void requireReturnsUnknownForUnrecognizedEvent() {
        assertEquals(VendingEventType.UNKNOWN, VendingEventType.require("SOME_RANDOM_EVENT"));
    }

    @Test
    void requireAllEventTypesExist() {
        assertEquals(8, VendingEventType.values().length);
    }

    @Test
    void requireValidThrowsOnNull() {
        assertThrows(PaymentGatewayException.class, () -> VendingEventType.requireValid(null));
    }

    @Test
    void requireValidThrowsOnBlank() {
        assertThrows(PaymentGatewayException.class, () -> VendingEventType.requireValid("   "));
    }

    @Test
    void requireValidThrowsOnUnknownType() {
        assertThrows(PaymentGatewayException.class, () -> VendingEventType.requireValid("NONEXISTENT"));
    }

    @Test
    void requireValidReturnsValidType() {
        assertEquals(VendingEventType.ASSET_RETURNED, VendingEventType.requireValid("ASSET_RETURNED"));
    }

    @Test
    void valueReturnsName() {
        assertEquals("ASSET_FAULT", VendingEventType.ASSET_FAULT.value());
    }
}
