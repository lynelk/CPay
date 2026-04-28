package net.citotech.cito;

import net.citotech.cito.Model.GatewayChargeDetails;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoPayGatewayChargeTest {

    private GatewayChargeDetails buildChargeDetails(String inMethod, double inCharge,
                                                      String outMethod, double outCharge,
                                                      String costInMethod, double costIn,
                                                      String costOutMethod, double costOut) {
        GatewayChargeDetails d = new GatewayChargeDetails();
        d.setCustomerInboundChargeMethod(inMethod);
        d.setCustomerInboundCharge(inCharge);
        d.setCustomerOutboundChargeMethod(outMethod);
        d.setCustomerOutboundCharge(outCharge);
        d.setCostOfPayInMethod(costInMethod);
        d.setCostOfInboundPayment(costIn);
        d.setCostOfPayOutMethod(costOutMethod);
        d.setCostOfOutboundPayment(costOut);
        d.setCustomerChargeMethod(inMethod);
        return d;
    }

    // --- getCustomerInboundCharges ---

    @Test
    void testCustomerInboundChargesPercentage() {
        GatewayChargeDetails d = buildChargeDetails("percentage", 2.5, "flat", 100, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerInboundCharges(1000.0, d);
        assertEquals(25.0, result, 0.001);
    }

    @Test
    void testCustomerInboundChargesFlat() {
        GatewayChargeDetails d = buildChargeDetails("flat", 150.0, "flat", 100, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerInboundCharges(1000.0, d);
        assertEquals(150.0, result, 0.001);
    }

    @Test
    void testCustomerInboundChargesNone() {
        GatewayChargeDetails d = buildChargeDetails("none", 0, "flat", 100, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerInboundCharges(1000.0, d);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    void testCustomerInboundChargesZeroAmount() {
        GatewayChargeDetails d = buildChargeDetails("percentage", 5.0, "flat", 100, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerInboundCharges(0.0, d);
        assertEquals(0.0, result, 0.001);
    }

    // --- getCustomerOutboundCharges ---

    @Test
    void testCustomerOutboundChargesPercentage() {
        GatewayChargeDetails d = buildChargeDetails("flat", 0, "percentage", 3.0, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerOutboundCharges(2000.0, d);
        assertEquals(60.0, result, 0.001);
    }

    @Test
    void testCustomerOutboundChargesFlat() {
        GatewayChargeDetails d = buildChargeDetails("flat", 0, "flat", 200.0, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerOutboundCharges(2000.0, d);
        assertEquals(200.0, result, 0.001);
    }

    // --- getCostOfInboundCharges ---

    @Test
    void testCostOfInboundChargesPercentage() {
        GatewayChargeDetails d = buildChargeDetails("flat", 0, "flat", 0, "percentage", 1.5, "flat", 0);
        Double result = DoPayGateway.getCostOfInboundCharges(1000.0, d);
        assertEquals(15.0, result, 0.001);
    }

    @Test
    void testCostOfInboundChargesFlat() {
        GatewayChargeDetails d = buildChargeDetails("flat", 0, "flat", 0, "flat", 50.0, "flat", 0);
        Double result = DoPayGateway.getCostOfInboundCharges(1000.0, d);
        assertEquals(50.0, result, 0.001);
    }

    // --- getCostOfOutboundCharges ---

    @Test
    void testCostOfOutboundChargesPercentage() {
        GatewayChargeDetails d = buildChargeDetails("flat", 0, "flat", 0, "flat", 0, "percentage", 2.0);
        Double result = DoPayGateway.getCostOfOutboundCharges(500.0, d);
        assertEquals(10.0, result, 0.001);
    }

    @Test
    void testCostOfOutboundChargesFlat() {
        GatewayChargeDetails d = buildChargeDetails("flat", 0, "flat", 0, "flat", 0, "flat", 75.0);
        Double result = DoPayGateway.getCostOfOutboundCharges(500.0, d);
        assertEquals(75.0, result, 0.001);
    }

    @Test
    void testHighPrecisionPercentage() {
        GatewayChargeDetails d = buildChargeDetails("percentage", 1.75, "flat", 0, "flat", 0, "flat", 0);
        Double result = DoPayGateway.getCustomerInboundCharges(10000.0, d);
        assertEquals(175.0, result, 0.001);
    }
}
