package net.citotech.cito.vending.connector;

import java.util.Locale;
import net.citotech.cito.gateway.PaymentGatewayException;

/**
 * Normalized vending domain events.
 *
 * <p>Vendor-specific event names (e.g. ChargeNow {@code BATTERY_BORROW_OUT}) are translated by the
 * vendor adapter into these normalized events before they touch the rental state machine, so the
 * business layer never hard-codes an OEM vocabulary. Unknown provider events are persisted as
 * {@link #UNKNOWN} rather than silently dropped.
 */
public enum VendingEventType {
    DEVICE_ONLINE,
    DEVICE_OFFLINE,
    DEVICE_STATUS,
    ASSET_RELEASED,
    ASSET_RETURNED,
    ASSET_FAULT,
    ASSET_POPUP,
    UNKNOWN;

    public static VendingEventType require(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public static VendingEventType requireValid(String value) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Vending event type is required");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new PaymentGatewayException("Unsupported vending event type: " + value);
        }
    }

    public String value() {
        return name();
    }
}
