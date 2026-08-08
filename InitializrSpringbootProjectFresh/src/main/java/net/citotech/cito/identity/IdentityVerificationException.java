package net.citotech.cito.identity;

import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * Raised when an identity provider cannot complete a verification (unreachable, misconfigured,
 * malformed response, or explicit provider rejection). The message is operator-facing and must
 * never contain raw PII.
 */
public class IdentityVerificationException extends PaymentGatewayException {

    public IdentityVerificationException(String message) {
        super(message);
    }
}
