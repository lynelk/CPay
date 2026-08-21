package net.citotech.cito.communication.provider;

import net.citotech.cito.communication.domain.CommunicationChannel;
import net.citotech.cito.communication.sms.SmsGatewayAdapter;
import net.citotech.cito.communication.sms.SmsSendRequest;
import net.citotech.cito.communication.sms.SmsSendResult;

/**
 * Compatibility bridge that adapts a legacy {@link SmsGatewayAdapter} into the channel-neutral
 * {@link CommunicationProviderAdapter} (ISO domain mapping: communication/provider). The existing
 * SMS adapters stay untouched; this wrapper lets the generic dispatcher and future router send SMS
 * through the same provider code used by {@code communication_routing_rules}.
 */
public final class SmsCommunicationProviderAdapter implements CommunicationProviderAdapter {

    private final SmsGatewayAdapter delegate;
    private final String providerCode;

    public SmsCommunicationProviderAdapter(SmsGatewayAdapter delegate, String providerCode) {
        this.delegate = delegate;
        this.providerCode = providerCode;
    }

    @Override
    public String providerCode() {
        return providerCode;
    }

    @Override
    public CommunicationChannel channel() {
        return CommunicationChannel.SMS;
    }

    @Override
    public ProviderCapabilities capabilities() {
        return ProviderCapabilities.builder().send(true).build();
    }

    @Override
    public ProviderSendResult send(ProviderSendRequest request) {
        SmsSendResult result =
                delegate.send(
                        new SmsSendRequest(
                                request.deliveryId(),
                                request.merchantId(),
                                request.content(),
                                request.recipient(),
                                providerCode));
        return SmsResultMapper.toProviderResult(providerCode, result);
    }
}
