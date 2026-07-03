package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class AirtelMoneyAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "airtel_money";
    public static final String LEGACY_GATEWAY_ID = "AIRTEL-MONEY";

    public AirtelMoneyAdapter() {
        super(CHANNEL_CODE, "Airtel Money", "UG", "UGX", LEGACY_GATEWAY_ID);
    }
}
