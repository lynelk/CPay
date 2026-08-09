package net.citotech.cito.communication.email;

/**
 * Outcome of one logical email send, normalized to the {SENT, FAILED} lifecycle (ISO domain
 * mapping: communication/email, track B2).
 *
 * <p>There is no REJECTED state at the SMTP boundary — a send either succeeds or throws. {@code
 * trace} carries the diagnostic detail (exception class/message, SMTP host) so failures stay
 * inspectable in the audit trail, replacing the legacy {@code SendMail} silent {@code
 * printStackTrace()} swallow. FAILED is refundable so the per-channel usage metering (communication
 * → billing, B5b) never invoices a message that was not delivered.
 */
public record EmailSendResult(Status status, String trace, String response) {

    public enum Status {
        SENT(false),
        FAILED(true);

        private final boolean refundable;

        Status(boolean refundable) {
            this.refundable = refundable;
        }

        public boolean isRefundable() {
            return refundable;
        }
    }

    public static EmailSendResult sent(String trace, String response) {
        return new EmailSendResult(Status.SENT, trace, response);
    }

    public static EmailSendResult failed(String trace, String response) {
        return new EmailSendResult(Status.FAILED, trace, response);
    }
}
