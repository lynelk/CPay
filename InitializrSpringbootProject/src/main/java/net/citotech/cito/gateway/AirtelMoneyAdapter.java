package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class AirtelMoneyAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "airtel_money";

    public AirtelMoneyAdapter() {
        super(CHANNEL_CODE, "Airtel Money", "UG", "UGX", LegacyGatewayIds.AIRTEL_MONEY, "25675", "25670", "25676");
    }
}
