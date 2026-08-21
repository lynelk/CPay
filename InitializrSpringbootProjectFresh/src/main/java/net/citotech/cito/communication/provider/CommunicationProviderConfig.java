package net.citotech.cito.communication.provider;

import net.citotech.cito.communication.sms.AfricasTalkingSmsGatewayAdapter;
import net.citotech.cito.communication.sms.LegacySettingsSmsGatewayAdapter;
import net.citotech.cito.communication.sms.TwilioSmsGatewayAdapter;
import net.citotech.cito.communication.sms.YoSmsGatewayAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers each legacy SMS adapter into the channel-neutral provider SPI as an individual bean
 * (ISO domain mapping: communication/provider). Because each wrapper is its own
 * {@code CommunicationProviderAdapter} bean, Spring auto-collects all of them into the {@link
 * ProviderRegistry}'s injected list. The provider codes match {@code communication_routing_rules}
 * (V50) so the generic dispatcher and future router send SMS through the exact same providers
 * without changing the existing adapters.
 */
@Configuration
public class CommunicationProviderConfig {

    @Bean
    public CommunicationProviderAdapter legacySettingsCommunicationProvider(
            LegacySettingsSmsGatewayAdapter delegate) {
        return new SmsCommunicationProviderAdapter(
                delegate, CommunicationSmsProviderCodes.LEGACY_SETTINGS);
    }

    @Bean
    public CommunicationProviderAdapter yoSmsCommunicationProvider(
            YoSmsGatewayAdapter delegate) {
        return new SmsCommunicationProviderAdapter(
                delegate, CommunicationSmsProviderCodes.YO_SMS);
    }

    @Bean
    public CommunicationProviderAdapter africastalkingCommunicationProvider(
            AfricasTalkingSmsGatewayAdapter delegate) {
        return new SmsCommunicationProviderAdapter(
                delegate, CommunicationSmsProviderCodes.AFRICAS_TALKING);
    }

    @Bean
    public CommunicationProviderAdapter twilioCommunicationProvider(
            TwilioSmsGatewayAdapter delegate) {
        return new SmsCommunicationProviderAdapter(
                delegate, CommunicationSmsProviderCodes.TWILIO_SMS);
    }
}
