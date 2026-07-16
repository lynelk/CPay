package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;
import org.springframework.stereotype.Component;

@Component
public class SafaricomMpesaAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "safaricom_mpesa";
    private final ProviderEndpointExecutionService executionService;

    public SafaricomMpesaAdapter(ProviderEndpointExecutionService executionService) {
        super(CHANNEL_CODE, "Safaricom M-Pesa", "KE", "KES", LegacyGatewayIds.SAFARICOM_MPESA, "25470", "25471", "25472", "25474", "25479", "25411");
        this.executionService = executionService;
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        return executionService.execute(CHANNEL_CODE, "Safaricom M-Pesa", "COLLECT", request);
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        return executionService.execute(CHANNEL_CODE, "Safaricom M-Pesa", "PAYOUT", request);
    }
}

