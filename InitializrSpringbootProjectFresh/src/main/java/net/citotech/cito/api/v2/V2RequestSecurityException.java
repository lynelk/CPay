package net.citotech.cito.api.v2;

import net.citotech.cito.gateway.PaymentGatewayException;

public class V2RequestSecurityException extends PaymentGatewayException {
    public V2RequestSecurityException(String message) {
        super(message);
    }
}

