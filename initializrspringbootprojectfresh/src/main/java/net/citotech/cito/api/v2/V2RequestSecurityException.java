package net.citotech.cito.api.v2;

import net.citotech.cito.gateway.PaymentGatewayException;

public class V2RequestSecurityException extends PaymentGatewayException {
    private static final long serialVersionUID = 1L;

    public V2RequestSecurityException(String message) {
        super(message);
    }
}

