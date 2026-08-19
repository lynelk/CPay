package net.citotech.cito.vending.connector;

import java.util.Locale;
import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * Normalized operations a vending connector can execute against a manufacturer/OEM platform.
 *
 * <p>The business layer only ever speaks these normalized operations. Each vendor profile supplies
 * the concrete HTTP mapping (method, path, body requirement, response fields) for the same
 * operation, so RELEASE_ASSET means "release the asset" regardless of whether the underlying OEM
 * calls it {@code /rent/order/create} or something else.
 */
public enum VendingConnectorOperation {
    RELEASE_ASSET,
    QUERY_RENTAL,
    GET_RENTAL_DETAIL,
    CLOSE_RENTAL,
    QUERY_DEVICE;

    public static VendingConnectorOperation require(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Vending connector operation is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException("Unsupported vending connector operation: " + value);
        }
    }

    public String value() {
        return name();
    }
}
