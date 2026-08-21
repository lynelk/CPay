package net.citotech.cito.communication.domain;

import java.util.Locale;

/**
 * Channel vocabulary for the CPay Communications Gateway (ISO domain mapping:
 * communication/domain). Every new application path converts an incoming channel string at the
 * API boundary into this enum instead of storing arbitrary free-form strings, so routing,
 * capability lookup, billing meters, and provider adapters share one stable contract.
 */
public enum CommunicationChannel {
    SMS,
    WHATSAPP,
    EMAIL,
    USSD,
    PUSH,
    IN_APP;

    /**
     * Parses a stored/incoming channel value. Returns {@code null} for missing or unrecognized
     * values (callers decide whether to default to SMS or reject, mirroring the legacy dispatcher's
     * permissive SMS default).
     */
    public static CommunicationChannel fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
