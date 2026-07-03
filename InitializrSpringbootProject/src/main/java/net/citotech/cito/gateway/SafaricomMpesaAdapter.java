package net.citotech.cito.gateway;

import org.springframework.stereotype.Component;

@Component
public class SafaricomMpesaAdapter extends LegacyGatewayAdapter {
    public static final String CHANNEL_CODE = "safaricom_mpesa";
    public static final String LEGACY_GATEWAY_ID = "SAFARICOM-MPESA";

    public SafaricomMpesaAdapter() {
        super(CHANNEL_CODE, "Safaricom M-Pesa", "KE", "KES", LEGACY_GATEWAY_ID);
    }
}
