package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class AirtelOpenApiAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "airtel_open_api";
    public static final String LEGACY_GATEWAY_ID = "AIRTEL-OPENAPI";

    public AirtelOpenApiAdapter() {
        super(CHANNEL_CODE, "Airtel OpenAPI", "UG", "UGX", LEGACY_GATEWAY_ID);
    }
}
