package net.citotech.cito.communication.sms;

import net.citotech.cito.Model.SmsDeliveryStatus;

/**
 * Outcome of one logical SMS send, normalized to the {PENDING, SENT, REJECTED, FAILED} lifecycle
 * from {@link SmsDeliveryStatus} (audit P5). The worker maps this onto the merchant_sms row: SENT
 * keeps the charge, REJECTED/FAILED reverse it. {@code trace} and {@code gwResponse} are the raw
 * HTTP internals for the audit columns (never exposed merchant-facing).
 */
public record SmsSendResult(SmsDeliveryStatus status, String trace, String gwResponse) {

    public static SmsSendResult sent(String trace, String gwResponse) {
        return new SmsSendResult(SmsDeliveryStatus.SENT, trace, gwResponse);
    }

    public static SmsSendResult rejected(String trace, String gwResponse) {
        return new SmsSendResult(SmsDeliveryStatus.REJECTED, trace, gwResponse);
    }

    public static SmsSendResult failed(String trace, String gwResponse) {
        return new SmsSendResult(SmsDeliveryStatus.FAILED, trace, gwResponse);
    }
}
