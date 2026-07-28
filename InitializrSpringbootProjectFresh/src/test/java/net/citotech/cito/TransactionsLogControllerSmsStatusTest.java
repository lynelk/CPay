package net.citotech.cito;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Covers audit P5: the SMS dispatch cron previously treated ANY HTTP response from the gateway
 * (including 4xx/5xx errors) as a successful send, so a provider-side rejection never refunded the
 * customer's charge. This is the boundary check that now distinguishes a real 2xx acceptance from
 * a provider rejection.
 */
class TransactionsLogControllerSmsStatusTest {

    private final TransactionsLogController controller = new TransactionsLogController();

    @Test
    void treats2xxAsSuccessful() {
        assertThat(controller.isSuccessfulSmsGatewayResponse(200)).isTrue();
        assertThat(controller.isSuccessfulSmsGatewayResponse(201)).isTrue();
        assertThat(controller.isSuccessfulSmsGatewayResponse(299)).isTrue();
    }

    @Test
    void treatsNon2xxAsARejection() {
        assertThat(controller.isSuccessfulSmsGatewayResponse(199)).isFalse();
        assertThat(controller.isSuccessfulSmsGatewayResponse(300)).isFalse();
        assertThat(controller.isSuccessfulSmsGatewayResponse(400)).isFalse();
        assertThat(controller.isSuccessfulSmsGatewayResponse(404)).isFalse();
        assertThat(controller.isSuccessfulSmsGatewayResponse(500)).isFalse();
        assertThat(controller.isSuccessfulSmsGatewayResponse(0)).isFalse();
    }
}
