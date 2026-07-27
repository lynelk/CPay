package net.citotech.cito.gateway;

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
}
