package net.citotech.cito.communication.sms;

/**
 * Immutable per-logical-SMS send request. Mirrors the fields the legacy pend-send batch passed to
 * the settings-driven HTTP gateway (content, comma-separated recipients, gateway name for the smsgw
 * audit column, and per-send ids for traceability).
 */
public record SmsSendRequest(
        long id, long merchantId, String content, String recipients, String gatewayName) {}
