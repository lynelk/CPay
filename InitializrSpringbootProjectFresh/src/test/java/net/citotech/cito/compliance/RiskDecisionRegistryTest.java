package net.citotech.cito.compliance;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import net.citotech.cito.Model.Merchant;
import net.citotech.cito.api.v2.dto.PaymentRequest;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Covers audit I1: the legacy payment path must run through the same risk authorization the v2
 * path already does. This registry is a static bridge (see PayoutCompensationSagaRegistry for the
 * established pattern), so every test here resets its static field afterward - left set, it would
 * leak a mock into every other test in the same JVM fork that touches Common.doPayIn/doPayOut.
 */
class RiskDecisionRegistryTest {

    @AfterEach
    void resetRegistry() {
        new RiskDecisionRegistry(null);
    }

    @Test
    void delegatesToTheWiredService() {
        RiskDecisionService service = mock(RiskDecisionService.class);
        new RiskDecisionRegistry(service);
        Merchant merchant = new Merchant();
        PaymentRequest request = new PaymentRequest();

        RiskDecisionRegistry.authorize(merchant, request, "COLLECT");

        verify(service).authorizePayment(merchant, request, "COLLECT");
    }

    @Test
    void propagatesABlockingDecisionAsAnException() {
        RiskDecisionService service = mock(RiskDecisionService.class);
        when(service.authorizePayment(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new PaymentGatewayException("blocklisted"));
        new RiskDecisionRegistry(service);

        assertThatThrownBy(() -> RiskDecisionRegistry.authorize(new Merchant(), new PaymentRequest(), "PAYOUT"))
            .isInstanceOf(PaymentGatewayException.class)
            .hasMessageContaining("blocklisted");
    }

    @Test
    void isANoOpWhenNoServiceHasBeenWired() {
        new RiskDecisionRegistry(null);

        // Must not throw - an uninitialized registry (test context) fails open, not closed.
        RiskDecisionRegistry.authorize(new Merchant(), new PaymentRequest(), "COLLECT");
    }
}
