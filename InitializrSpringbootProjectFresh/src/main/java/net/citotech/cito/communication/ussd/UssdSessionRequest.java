package net.citotech.cito.communication.ussd;

/** Normalized inbound USSD session request from an aggregator/provider. */
public record UssdSessionRequest(long merchantId, String sessionId, String msisdn, String input) {
    public UssdSessionRequest {
        if (merchantId <= 0) throw new IllegalArgumentException("merchantId must be positive");
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("sessionId is required");
        if (msisdn == null || msisdn.isBlank()) throw new IllegalArgumentException("msisdn is required");
        input = input == null ? "" : input;
    }
}
