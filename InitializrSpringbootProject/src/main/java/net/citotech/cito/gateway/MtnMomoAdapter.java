package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;
import org.springframework.stereotype.Component;

@Component
public class MtnMomoAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "mtn_momo";

    public MtnMomoAdapter() {
        super(CHANNEL_CODE, "MTN MoMo", "UG", "UGX", LegacyGatewayIds.MTN_MOMO, "25677", "25678", "25676");
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "MTN MoMo", "COLLECT", request);
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "MTN MoMo", "PAYOUT", request);
    }
}
