package net.citotech.cito.gateway;

/** Runtime exception for payment orchestration and adapter failures. */
public class PaymentGatewayException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public PaymentGatewayException(String message) {
        super(message);
    }

    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}

