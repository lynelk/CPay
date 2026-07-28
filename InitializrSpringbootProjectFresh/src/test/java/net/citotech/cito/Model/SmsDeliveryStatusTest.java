package net.citotech.cito.Model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Covers audit P5: REJECTED and FAILED must both be treated as refundable; SENT/CANCELLED/PENDING must not. */
class SmsDeliveryStatusTest {

    @Test
    void rejectedAndFailedAreRefundable() {
        assertThat(SmsDeliveryStatus.REJECTED.isRefundable()).isTrue();
        assertThat(SmsDeliveryStatus.FAILED.isRefundable()).isTrue();
    }

    @Test
    void sentCancelledAndPendingAreNotRefundable() {
        assertThat(SmsDeliveryStatus.SENT.isRefundable()).isFalse();
        assertThat(SmsDeliveryStatus.CANCELLED.isRefundable()).isFalse();
        assertThat(SmsDeliveryStatus.PENDING.isRefundable()).isFalse();
    }

    @Test
    void fromStringIsCaseInsensitiveAndNullSafe() {
        assertThat(SmsDeliveryStatus.fromString("rejected")).isEqualTo(SmsDeliveryStatus.REJECTED);
        assertThat(SmsDeliveryStatus.fromString("SENT")).isEqualTo(SmsDeliveryStatus.SENT);
        assertThat(SmsDeliveryStatus.fromString("bogus")).isNull();
        assertThat(SmsDeliveryStatus.fromString(null)).isNull();
    }
}
