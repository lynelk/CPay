package net.citotech.cito.communication;

import java.util.HashMap;
import java.util.Map;
import net.citotech.cito.communication.routing.ProviderRouter;
import net.citotech.cito.communication.sms.AfricasTalkingSmsGatewayAdapter;
import net.citotech.cito.communication.sms.LegacySettingsSmsGatewayAdapter;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import net.citotech.cito.communication.sms.TwilioSmsGatewayAdapter;
import net.citotech.cito.communication.sms.YoSmsGatewayAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registration point for SMS provider adapters behind the {@link ProviderRouter} (ISO domain
 * mapping: communication/routing, track B1a). The map keys are the {@code provider_code}s used by
 * {@code communication_routing_rules} (V50). {@code LEGACY_SETTINGS} must always be present: the
 * router falls back to it for unconfigured/unknown targets and on lookup failure, so existing
 * deployments keep the exact pre-router behavior.
 *
 * <p>B1B provider adapters (Yo! SMS, Africa's Talking, Twilio) register their codes here as they
 * land. The map is built explicitly (never Spring's auto-collected interface map) so the router is
 * never its own delegate.
 */
@Configuration
public class CommunicationSmsConfig {

    public static final String LEGACY_SETTINGS_CODE = "LEGACY_SETTINGS";

    @Bean
    public Map<String, SmsGatewayAdapter> smsAdaptersByCode(
            LegacySettingsSmsGatewayAdapter legacySettingsSmsGatewayAdapter,
            YoSmsGatewayAdapter yoSmSmsGatewayAdapter,
            AfricasTalkingSmsGatewayAdapter africastalkingSmsGatewayAdapter,
            TwilioSmsGatewayAdapter twilioSmsGatewayAdapter) {
        Map<String, SmsGatewayAdapter> adapters = new HashMap<>();
        adapters.put(LEGACY_SETTINGS_CODE, legacySettingsSmsGatewayAdapter);
        adapters.put("YO_SMS", yoSmSmsGatewayAdapter);
        adapters.put("AFRICAS_TALKING", africastalkingSmsGatewayAdapter);
        adapters.put("TWILIO_SMS", twilioSmsGatewayAdapter);
        return Map.copyOf(adapters);
    }
}
