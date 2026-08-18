package net.citotech.cito.vending.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingEventTypeTest {

    @Test
    void enumeratesNormalizedDomainEvents() {
        assertThat(VendingEventType.values())
                .containsExactly(
                        VendingEventType.DEVICE_ONLINE,
                        VendingEventType.DEVICE_OFFLINE,
                        VendingEventType.DEVICE_STATUS,
                        VendingEventType.ASSET_RELEASED,
                        VendingEventType.ASSET_RETURNED,
                        VendingEventType.ASSET_FAULT,
                        VendingEventType.ASSET_POPUP,
                        VendingEventType.UNKNOWN);
    }

    @Test
    void requireAcceptsNormalizedEventNamesAndNormalizesCase() {
        assertThat(VendingEventType.require("ASSET_RELEASED"))
                .isEqualTo(VendingEventType.ASSET_RELEASED);
        assertThat(VendingEventType.require("asset_returned"))
                .isEqualTo(VendingEventType.ASSET_RETURNED);
        assertThat(VendingEventType.require("device_online"))
                .isEqualTo(VendingEventType.DEVICE_ONLINE);
    }

    @Test
    void requireMapsUnknownProviderEventNamesToUnknownWithoutDiscarding() {
        // Vendor-specific names (e.g. ChargeNow BATTERY_BORROW_OUT) are translated into these
        // normalized events by the vendor adapter/event translator, never by the generic enum.
        assertThat(VendingEventType.require("BATTERY_BORROW_OUT"))
                .isEqualTo(VendingEventType.UNKNOWN);
        assertThat(VendingEventType.require("CABINET_ALARM_XYZ"))
                .isEqualTo(VendingEventType.UNKNOWN);
        assertThat(VendingEventType.require(null)).isEqualTo(VendingEventType.UNKNOWN);
    }

    @Test
    void requireValidRejectsUnknownEvent() {
        assertThatThrownBy(() -> VendingEventType.requireValid("BATTERY_BORROW_OUT"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported vending event type");
    }
}
