package net.citotech.cito.vending.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.citotech.cito.gateway.PaymentGatewayException;
import org.junit.jupiter.api.Test;

class VendingConnectorOperationTest {

    @Test
    void enumeratesTheFiveNormalizedOperations() {
        assertThat(VendingConnectorOperation.values())
                .containsExactly(
                        VendingConnectorOperation.RELEASE_ASSET,
                        VendingConnectorOperation.QUERY_RENTAL,
                        VendingConnectorOperation.GET_RENTAL_DETAIL,
                        VendingConnectorOperation.CLOSE_RENTAL,
                        VendingConnectorOperation.QUERY_DEVICE);
    }

    @Test
    void requireAcceptsNormalizedNames() {
        assertThat(VendingConnectorOperation.require("release_asset"))
                .isEqualTo(VendingConnectorOperation.RELEASE_ASSET);
        assertThat(VendingConnectorOperation.require("QUERY_DEVICE"))
                .isEqualTo(VendingConnectorOperation.QUERY_DEVICE);
    }

    @Test
    void requireRejectsUnknownOperation() {
        assertThatThrownBy(() -> VendingConnectorOperation.require("EJECT"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unsupported vending connector operation");
    }
}
