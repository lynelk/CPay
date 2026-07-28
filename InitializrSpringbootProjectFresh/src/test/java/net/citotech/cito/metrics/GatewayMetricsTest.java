package net.citotech.cito.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Covers audit H3: GatewayMetrics' counters existed but were only ever wired into callback
 * delivery and rate limiting - incrementTransactionInitiated/Completed and incrementGatewayError
 * (now called from PaymentOrchestrationService.collect/payout) had no test coverage at all.
 * Verifies each counter records under the expected name/tags so a real MeterRegistry (e.g.
 * Prometheus) would actually see these on the metrics endpoint.
 */
class GatewayMetricsTest {

    @Test
    void recordsTransactionInitiated() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);

        metrics.incrementTransactionInitiated("MTNMoMoPaymentGateway", "PAYIN");

        assertThat(registry.get("cpay.transaction.initiated")
            .tag("gateway_id", "MTNMoMoPaymentGateway")
            .tag("tx_type", "PAYIN")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordsTransactionCompletedWithStatusTag() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);

        metrics.incrementTransactionCompleted("AirtelMoneyPaymentGateway", "PAYOUT", "SUCCESSFUL");
        metrics.incrementTransactionCompleted("AirtelMoneyPaymentGateway", "PAYOUT", "SUCCESSFUL");
        metrics.incrementTransactionCompleted("AirtelMoneyPaymentGateway", "PAYOUT", "FAILED");

        assertThat(registry.get("cpay.transaction.completed")
            .tag("gateway_id", "AirtelMoneyPaymentGateway")
            .tag("tx_type", "PAYOUT")
            .tag("status", "SUCCESSFUL")
            .counter().count()).isEqualTo(2.0);
        assertThat(registry.get("cpay.transaction.completed")
            .tag("gateway_id", "AirtelMoneyPaymentGateway")
            .tag("tx_type", "PAYOUT")
            .tag("status", "FAILED")
            .counter().count()).isEqualTo(1.0);
    }

    @Test
    void recordsGatewayErrorsPerGateway() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);

        metrics.incrementGatewayError("SafariComPaymentGateway");

        assertThat(registry.get("cpay.gateway.error")
            .tag("gateway_id", "SafariComPaymentGateway")
            .counter().count()).isEqualTo(1.0);
    }
}
