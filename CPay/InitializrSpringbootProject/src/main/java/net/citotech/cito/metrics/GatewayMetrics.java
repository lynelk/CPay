package net.citotech.cito.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Business metrics for the CPay gateway.
 * Exposes counters for transactions, callbacks, and errors by gateway.
 */
@Component
public class GatewayMetrics {

    private final MeterRegistry meterRegistry;

    @Autowired
    public GatewayMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void incrementTransactionInitiated(String gatewayId, String txType) {
        Counter.builder("cpay.transaction.initiated")
                .tag("gateway_id", gatewayId)
                .tag("tx_type", txType)
                .description("Number of transactions initiated")
                .register(meterRegistry)
                .increment();
    }

    public void incrementTransactionCompleted(String gatewayId, String txType, String status) {
        Counter.builder("cpay.transaction.completed")
                .tag("gateway_id", gatewayId)
                .tag("tx_type", txType)
                .tag("status", status)
                .description("Number of transactions completed")
                .register(meterRegistry)
                .increment();
    }

    public void incrementCallbackDelivery(String status) {
        Counter.builder("cpay.callback.delivery")
                .tag("status", status)
                .description("Callback delivery outcomes")
                .register(meterRegistry)
                .increment();
    }

    public void incrementGatewayError(String gatewayId) {
        Counter.builder("cpay.gateway.error")
                .tag("gateway_id", gatewayId)
                .description("Gateway API errors")
                .register(meterRegistry)
                .increment();
    }

    public void incrementRateLimitHit(String merchantNumber) {
        Counter.builder("cpay.rate_limit.exceeded")
                .tag("merchant", merchantNumber)
                .description("Rate limit exceeded events")
                .register(meterRegistry)
                .increment();
    }
}
