package net.citotech.cito.communication.delivery;

/**
 * Channel-agnostic lifecycle for {@code communication_message_deliveries} (V53, track B5a). Mirrors
 * the {@code merchant_sms} status shape from {@code SmsDeliveryStatus} so the existing SMS
 * semantics extend to EMAIL/WHATSAPP/USSD without a parallel vocabulary: a REJECTED or FAILED
 * outcome is refundable (audit P5), which the communication-to-billing meter relay relies on to
 * never invoice an undelivered message.
 */
public enum DeliveryStatus {
    PENDING,
    CANCELLED,
    SENT,
    REJECTED,
    FAILED;

    /** REJECTED and FAILED both mean the message never went out and must not be billed. */
    public boolean isRefundable() {
        return this == REJECTED || this == FAILED;
    }

    public static DeliveryStatus fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
