package net.citotech.cito.communication.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.citotech.cito.Model.SmsDeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Covers the fail-safe credential guard of the B1B provider adapters: with no provider settings in
 * the store, each adapter returns a refundable FAILED (audit P5) instead of attempting a network
 * call or crashing the batch. The HTTP send path itself is exercised by the provider sandbox /
 * WireMock lane (opt-in, Docker-gated) per the existing provider-certification practice.
 */
class ProviderSmsAdaptersTest {

    private NamedParameterJdbcTemplate emptySettingsJdbc() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());
        return jdbcTemplate;
    }

    private SmsSendRequest request() {
        return new SmsSendRequest(1L, 7L, "Hello", "256700000001", "unknown");
    }

    @Test
    void yoAdapterReturnsRefundableFailureWhenCredentialsAreMissing() {
        SmsGatewayAdapter adapter = new YoSmsGatewayAdapter(emptySettingsJdbc());

        SmsSendResult result = adapter.send(request());

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(result.status().isRefundable()).isTrue();
        assertThat(result.trace()).contains("yo_sms_username");
    }

    @Test
    void africastalkingAdapterReturnsRefundableFailureWhenCredentialsAreMissing() {
        SmsGatewayAdapter adapter = new AfricasTalkingSmsGatewayAdapter(emptySettingsJdbc());

        SmsSendResult result = adapter.send(request());

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(result.status().isRefundable()).isTrue();
        assertThat(result.trace()).contains("africastalking_username");
    }

    @Test
    void twilioAdapterReturnsRefundableFailureWhenCredentialsAreMissing() {
        SmsGatewayAdapter adapter = new TwilioSmsGatewayAdapter(emptySettingsJdbc());

        SmsSendResult result = adapter.send(request());

        assertThat(result.status()).isEqualTo(SmsDeliveryStatus.FAILED);
        assertThat(result.status().isRefundable()).isTrue();
        assertThat(result.trace()).contains("twilio_account_sid");
    }
}
