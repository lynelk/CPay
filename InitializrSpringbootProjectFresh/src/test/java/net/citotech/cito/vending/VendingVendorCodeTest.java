package net.citotech.cito.vending;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingVendorCodeTest {

    @Test
    void requireAcceptsChargeNowAndNormalizesCase() {
        assertThat(VendingVendorCode.require("CHARGENOW")).isEqualTo(VendingVendorCode.CHARGENOW);
        assertThat(VendingVendorCode.require("chargenow")).isEqualTo(VendingVendorCode.CHARGENOW);
    }

    @Test
    void requireRejectsUnknownVendor() {
        assertThatThrownBy(() -> VendingVendorCode.require("VENDOR_B"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported vending vendor code");
    }

    @Test
    void requireRejectsBlankVendor() {
        assertThatThrownBy(() -> VendingVendorCode.require("  "))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("required");
    }
}
