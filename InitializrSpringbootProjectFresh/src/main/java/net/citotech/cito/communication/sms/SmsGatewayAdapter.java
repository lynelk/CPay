package net.citotech.cito.communication.sms;

/**
 * Boundary for SMS provider delivery (ISO domain mapping: communication/sms).
 *
 * <p>The legacy send path fused provider delivery into {@code TransactionsLogController} with a
 * single settings-driven HTTP gateway. This seam lets B1B provider adapters (Yo! SMS, Africa's
 * Talking, Twilio) plug in without touching the billing/delivery worker, and keeps the worker
 * testable against a fake adapter.
 */
public interface SmsGatewayAdapter {

    /**
     * Sends one logical SMS. A logical send may fan out to one HTTP request per recipient for
     * per-phone gateways (e.g. speedamobile); the returned result reflects the last request that
     * was made, matching the legacy behavior.
     */
    SmsSendResult send(SmsSendRequest request);
}
