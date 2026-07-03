package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class AirtelOpenApiAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "airtel_open_api";

    public AirtelOpenApiAdapter() {
        super(CHANNEL_CODE, "Airtel OpenAPI", "UG", "UGX", LegacyGatewayIds.AIRTEL_OPEN_API, "25675", "25670", "25676");
    }
}
