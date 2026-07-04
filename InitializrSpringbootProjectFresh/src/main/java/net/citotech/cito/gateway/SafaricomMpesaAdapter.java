package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;
import org.springframework.stereotype.Component;

@Component
public class SafaricomMpesaAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "safaricom_mpesa";

    public SafaricomMpesaAdapter() {
        super(CHANNEL_CODE, "Safaricom M-Pesa", "KE", "KES", LegacyGatewayIds.SAFARICOM_MPESA, "25470", "25471", "25472", "25474", "25479", "25411");
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "Safaricom M-Pesa", "COLLECT", request);
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "Safaricom M-Pesa", "PAYOUT", request);
    }
}

