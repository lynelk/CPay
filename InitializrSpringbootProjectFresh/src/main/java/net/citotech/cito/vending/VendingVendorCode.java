package net.citotech.cito.vending;

import java.util.Locale;
import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * Normalized vending vendor codes.
 *
 * <p>A vending vendor is the manufacturer/OEM that owns the physical cabinet and rental execution
 * (currently CHARGENOW / Bajie Charging). It is deliberately separate from the payment provider
 * that moves the money (MTN_MOMO, AIRTEL_MONEY, ...). New vendors are added here without touching
 * the rental, pricing or payment engines.
 */
public enum VendingVendorCode {
    CHARGENOW;

    public static VendingVendorCode require(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Vending vendor code is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException("Unsupported vending vendor code: " + value);
        }
    }

    public String value() {
        return name();
    }
}
