package net.citotech.cito.webhook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.Model.HttpRequestResponse;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class MerchantWebhookServiceTest {

    @Test
    void registersEndpointAndReturnsPlainSecretOnce() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(cryptoService.encrypt(anyString()))
                .thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        Map<String, Object> result =
                service.registerEndpoint(
                        15L, "payment.pending", "https://merchant.test/webhook", "tester");

        assertThat(result.get("code")).isEqualTo("000");
        assertThat((String) result.get("secret")).hasSizeGreaterThan(40);
        verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void enqueueCreatesDeliveryForEveryActiveEndpoint() throws Exception {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getLong("id")).thenReturn(99L);
                            return List.of(mapper.mapRow(rs, 0));
                        });
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        assertThat(service.enqueue(15L, "payment.pending", "TX-1", "{}")).isEqualTo(1);
    }

    @Test
    void registerRejectsAnEventTypeNotInTheCatalog() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        assertThatThrownBy(
                        () ->
                                service.registerEndpoint(
                                        15L,
                                        "payment.pendingg",
                                        "https://merchant.test/webhook",
                                        "tester"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unknown webhook event type");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void enqueueRejectsAnEventTypeNotInTheCatalog() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        assertThatThrownBy(() -> service.enqueue(15L, "not.a.real.event", "TX-1", "{}"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Unknown webhook event type");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void enqueueAddsTheVersionedEnvelopeFieldsOnTopOfTheCallersPayload() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getLong("id")).thenReturn(99L);
                            return List.of(mapper.mapRow(rs, 0));
                        });
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        service.enqueue(15L, "payment.pending", "TX-1", "{\"reference\":\"TX-1\"}");

        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        JSONObject stored = new JSONObject((String) captor.getValue().getValue("payload_json"));
        assertThat(stored.getString("reference")).isEqualTo("TX-1");
        assertThat(stored.getInt("eventVersion")).isEqualTo(1);
        assertThat(stored.getString("eventId")).isNotBlank();
        assertThat(stored.getString("createdAt")).isNotBlank();
    }

    @Test
    void merchantScopedRotateSecretIncludesMerchantIdInTheUpdate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(cryptoService.encrypt(anyString()))
                .thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        Map<String, Object> result = service.rotateSecret(15L, 99L);

        assertThat(result.get("code")).isEqualTo("000");
        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        assertThat(captor.getValue().getValue("merchant_id")).isEqualTo(15L);
        assertThat(captor.getValue().getValue("id")).isEqualTo(99L);
    }

    @Test
    void merchantScopedRotateSecretRejectsAnEndpointBelongingToAnotherMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        // Simulates the WHERE id=:id AND merchant_id=:merchant_id clause matching zero rows because
        // endpoint 99 belongs to a different merchant than 15.
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        assertThatThrownBy(() -> service.rotateSecret(15L, 99L))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void merchantScopedReplayIncludesMerchantIdInTheUpdate() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        int updated = service.replay(15L, 42L);

        assertThat(updated).isEqualTo(1);
        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        assertThat(captor.getValue().getValue("merchant_id")).isEqualTo(15L);
        assertThat(captor.getValue().getValue("id")).isEqualTo(42L);
    }

    @Test
    void merchantScopedReplayReturnsZeroForADeliveryBelongingToAnotherMerchant() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(0);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        assertThat(service.replay(15L, 42L)).isZero();
    }

    @Test
    void listDeliveriesQueriesByMerchantIdAndClampsTheLimit() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.queryForList(anyString(), any(MapSqlParameterSource.class)))
                .thenReturn(List.of());
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        service.listDeliveries(15L, 500);

        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).queryForList(anyString(), captor.capture());
        assertThat(captor.getValue().getValue("merchant_id")).isEqualTo(15L);
        assertThat(captor.getValue().getValue("limit")).isEqualTo(200);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void enqueuePersistsTheDeliveryNonceAndTimestampAlongsideThePayload() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getLong("id")).thenReturn(99L);
                            return List.of(mapper.mapRow(rs, 0));
                        });
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        service.enqueue(15L, "payment.pending", "TX-1", "{\"reference\":\"TX-1\"}");

        ArgumentCaptor<MapSqlParameterSource> captor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate).update(anyString(), captor.capture());
        String nonce = (String) captor.getValue().getValue("delivery_nonce");
        assertThat(nonce).isNotBlank();
        assertThat(captor.getValue().getValue("delivery_timestamp"))
                .isInstanceOf(Timestamp.class);
        JSONObject stored = new JSONObject((String) captor.getValue().getValue("payload_json"));
        assertThat(stored.getString("eventId")).isEqualTo(nonce);
        assertThat(stored.getString("createdAt")).isNotBlank();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void exhaustedAttemptsParkTheDeliveryAndRecordAPerAttemptAuditRow() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(cryptoService.decrypt(anyString())).thenReturn("plain-secret");
        when(jdbcTemplate.query(
                        anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(
                        invocation -> {
                            RowMapper mapper = invocation.getArgument(2);
                            ResultSet rs = mock(ResultSet.class);
                            when(rs.getLong("id")).thenReturn(42L);
                            when(rs.getString("event_type")).thenReturn("payment.pending");
                            when(rs.getString("event_reference")).thenReturn("TX-1");
                            when(rs.getString("payload_json")).thenReturn("{}");
                            when(rs.getInt("attempt_count")).thenReturn(4);
                            when(rs.getString("endpoint_url"))
                                    .thenReturn("https://merchant.test/hook");
                            when(rs.getString("secret_value")).thenReturn("enc:secret");
                            when(rs.getString("delivery_nonce")).thenReturn("nonce-1");
                            when(rs.getTimestamp("delivery_timestamp"))
                                    .thenReturn(Timestamp.from(Instant.now()));
                            return List.of(mapper.mapRow(rs, 0));
                        });
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        try (MockedStatic<Common> common = mockStatic(Common.class)) {
            // Every attempt is a transport failure (no response), so attempt #5 exhausts the
            // schedule and the delivery must PARK instead of silently dropping to FAILED.
            when(Common.doHttpRequest(anyString(), anyString(), anyString(), anyMap()))
                    .thenReturn(null);

            int processed = service.deliverDue(1);

            assertThat(processed).isEqualTo(1);
        }

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> paramsCaptor =
                ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbcTemplate, times(2)).update(sqlCaptor.capture(), paramsCaptor.capture());
        // First update: the delivery row itself flips to PARKED (status bound via :status param,
        // so the park clause proves the PARKED path was taken) with the exhausted-attempt reason.
        assertThat(sqlCaptor.getAllValues().get(0)).contains("UPDATE merchant_webhook_deliveries");
        assertThat(sqlCaptor.getAllValues().get(0))
                .contains("parked_at=CURRENT_TIMESTAMP")
                .contains("park_reason=:park_reason");
        assertThat(paramsCaptor.getAllValues().get(0).getValue("status")).isEqualTo("PARKED");
        assertThat(paramsCaptor.getAllValues().get(0).getValue("attempts")).isEqualTo(5);
        // Second update: the per-attempt audit row records attempt #5 with the same nonce.
        assertThat(sqlCaptor.getAllValues().get(1))
                .contains("INSERT INTO merchant_webhook_delivery_attempts");
        assertThat(paramsCaptor.getAllValues().get(1).getValue("delivery_id")).isEqualTo(42L);
        assertThat(paramsCaptor.getAllValues().get(1).getValue("attempt")).isEqualTo(5);
        assertThat(paramsCaptor.getAllValues().get(1).getValue("status")).isEqualTo("PARKED");
        assertThat(paramsCaptor.getAllValues().get(1).getValue("nonce")).isEqualTo("nonce-1");
    }

    @Test
    void replayUnparksAParkedDelivery() {
        NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
        MerchantChannelCryptoService cryptoService = mock(MerchantChannelCryptoService.class);
        when(jdbcTemplate.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        MerchantWebhookService service = new MerchantWebhookService(jdbcTemplate, cryptoService);

        service.replay(42L);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sqlCaptor.capture(), any(MapSqlParameterSource.class));
        assertThat(sqlCaptor.getValue()).contains("PARKED");
        assertThat(sqlCaptor.getValue()).contains("parked_at=NULL");
    }
}
