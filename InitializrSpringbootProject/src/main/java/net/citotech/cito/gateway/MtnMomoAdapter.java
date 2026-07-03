package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class MtnMomoAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "mtn_momo";
    public static final String LEGACY_GATEWAY_ID = "MTN-MOMO";

    public MtnMomoAdapter() {
        super(CHANNEL_CODE, "MTN MoMo", "UG", "UGX", LEGACY_GATEWAY_ID);
    }
}
