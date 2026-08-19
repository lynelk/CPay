package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingConnectorOperationTest {

    @Test
    void requireReturnsValidEnum() {
        assertEquals(VendingConnectorOperation.RELEASE_ASSET, VendingConnectorOperation.require("RELEASE_ASSET"));
    }

    @Test
    void requireNormalizesCase() {
        assertEquals(VendingConnectorOperation.QUERY_RENTAL, VendingConnectorOperation.require("query_rental"));
    }

    @Test
    void requireAllOperationsExist() {
        assertEquals(5, VendingConnectorOperation.values().length);
        assertEquals(VendingConnectorOperation.RELEASE_ASSET, VendingConnectorOperation.require("RELEASE_ASSET"));
        assertEquals(VendingConnectorOperation.QUERY_RENTAL, VendingConnectorOperation.require("QUERY_RENTAL"));
        assertEquals(VendingConnectorOperation.GET_RENTAL_DETAIL, VendingConnectorOperation.require("GET_RENTAL_DETAIL"));
        assertEquals(VendingConnectorOperation.CLOSE_RENTAL, VendingConnectorOperation.require("CLOSE_RENTAL"));
        assertEquals(VendingConnectorOperation.QUERY_DEVICE, VendingConnectorOperation.require("QUERY_DEVICE"));
    }

    @Test
    void requireRejectsNull() {
        assertThrows(PaymentGatewayException.class, () -> VendingConnectorOperation.require(null));
    }

    @Test
    void requireRejectsBlank() {
        assertThrows(PaymentGatewayException.class, () -> VendingConnectorOperation.require("   "));
    }

    @Test
    void requireRejectsUnknownOperation() {
        assertThrows(PaymentGatewayException.class, () -> VendingConnectorOperation.require("INVALID"));
    }

    @Test
    void valueReturnsName() {
        assertEquals("QUERY_DEVICE", VendingConnectorOperation.QUERY_DEVICE.value());
    }
}
