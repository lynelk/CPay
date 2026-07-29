package net.citotech.cito.gateway;

import java.util.Map;
import net.citotech.cito.Model.GateWayResponse;
import org.springframework.stereotype.Component;

@Component
public class YoPaymentsAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "yo_payments";
    private final ProviderEndpointExecutionService executionService;

    public YoPaymentsAdapter(ProviderEndpointExecutionService executionService) {
        super(CHANNEL_CODE, "Yo! Payments", "UG", "UGX", LegacyGatewayIds.YO_PAYMENTS);
        this.executionService = executionService;
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        return executionService.execute(CHANNEL_CODE, "Yo! Payments", "COLLECT", request);
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        return executionService.execute(CHANNEL_CODE, "Yo! Payments", "PAYOUT", request);
    }

    /**
     * Audit C9: {@link ProviderEndpointExecutionService} already enforces this same check
     * directly (it is the only component that ever sees the provider's raw response), so
     * this override mainly makes the capability reachable through the adapter contract
     * itself - e.g. for callers that hold a {@code PaymentChannelAdapter} reference rather
     * than knowing this is execution-service-backed. See {@link YoPaymentsCallbackVerifier}
     * for the actual verification logic and the reasoning behind it.
     */
    @Override
    public boolean verifyCallback(Map<String, String> responseHeaders, String responseBody, Map<String, String> channelConfig) {
        return YoPaymentsCallbackVerifier.verify(responseHeaders, responseBody, channelConfig);
    }
}
