package net.citotech.cito.gateway;

import net.citotech.cito.Model.GateWayResponse;
import org.springframework.stereotype.Component;

@Component
public class AirtelOpenApiAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "airtel_open_api";

    public AirtelOpenApiAdapter() {
        super(CHANNEL_CODE, "Airtel OpenAPI", "UG", "UGX", LegacyGatewayIds.AIRTEL_OPEN_API, "25675", "25670", "25676");
    }

    @Override
    public GateWayResponse collect(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "Airtel OpenAPI", "COLLECT", request);
    }

    @Override
    public GateWayResponse payout(PaymentGatewayRequest request) {
        return ProviderEndpointClient.execute(CHANNEL_CODE, "Airtel OpenAPI", "PAYOUT", request);
    }
}
