package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class MtnMomoAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "mtn_momo";

    public MtnMomoAdapter() {
        super(CHANNEL_CODE, "MTN MoMo", "UG", "UGX", LegacyGatewayIds.MTN_MOMO, "25677", "25678", "25676");
    }
}
