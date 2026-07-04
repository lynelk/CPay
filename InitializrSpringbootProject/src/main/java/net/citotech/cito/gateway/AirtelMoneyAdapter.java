package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;
import org.springframework.stereotype.Component;

@Component
public class AirtelMoneyAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "airtel_money";

    public AirtelMoneyAdapter() {
        super(CHANNEL_CODE, "Airtel", "UG", "UGX", LegacyGatewayIds.AIRTEL_MONEY, "25675", "25670", "25676");
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "Airtel", "COLLECT", request);
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "Airtel", "PAYOUT", request);
    }
}
