package net.citotech.cito.communication.whatsapp;

/** Provider adapter boundary for WhatsApp delivery. */
public interface WhatsAppGatewayAdapter {
    String providerCode();

    WhatsAppSendResult send(WhatsAppSendRequest request);
}
