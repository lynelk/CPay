package net.citotech.cito.communication.ussd;

/** Provider-neutral USSD response. action is CON (continue) or END (terminate). */
public record UssdSessionResponse(String sessionId, String action, String message) {
    public String providerText() {
        return action + " " + message;
    }
}
