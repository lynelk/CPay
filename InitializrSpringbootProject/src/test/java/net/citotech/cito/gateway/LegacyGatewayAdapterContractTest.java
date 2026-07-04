package net.citotech.cito.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import net.citotech.cito.Model.GateWayResponse;
import org.junit.jupiter.api.Test;

class LegacyGatewayAdapterContractTest {
    @Test
    void adapterNativeCollectReturnsSubmittedResponse() {
        LegacyGatewayAdapter adapter = new LegacyGatewayAdapter("test_channel", "Test Channel", "UG", "UGX", "TestGateway", "256") {};
        PaymentGatewayRequest request = new PaymentGatewayRequest("1000000", "256770000000", 1000.00, "REF-1", "Test", "callback", Collections.emptyMap());
        GateWayResponse response = adapter.collect(request);
        assertEquals("SUBMITTED", response.getTransactionStatus());
        assertTrue(response.getNetworkId().contains("REF-1"));
    }
}
