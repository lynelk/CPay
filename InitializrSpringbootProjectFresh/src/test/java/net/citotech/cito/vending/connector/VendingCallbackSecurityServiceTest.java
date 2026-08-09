package net.citotech.cito.vending.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

class VendingCallbackSecurityServiceTest {
    private static final String SECRET = "manufacturer-callback-secret";

    @Test
    void validTimestampNonceAndBodyHmacIsAccepted() throws Exception {
        var configurations = mock(VendingConnectorConfigurationService.class);
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract());
        when(jdbc.update(any(String.class), any(MapSqlParameterSource.class))).thenReturn(1);
        var service = new VendingCallbackSecurityService(configurations, jdbc);

        String body = "{\"eventId\":\"evt-1\",\"eventType\":\"HEARTBEAT\",\"deviceId\":\"CAB-1\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-1";
        var request = request(timestamp, nonce, sign(timestamp + "\n" + nonce + "\n" + body));

        Contract verified = service.verify(7L, "CHARGENOW", request, body);
        assertEquals("CHARGENOW", verified.connectorCode());
    }

    @Test
    void invalidSignatureIsRejectedBeforeNonceClaim() {
        var configurations = mock(VendingConnectorConfigurationService.class);
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract());
        var service = new VendingCallbackSecurityService(configurations, jdbc);

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        var request = request(timestamp, "nonce-2", Base64.getEncoder().encodeToString("wrong".getBytes(StandardCharsets.UTF_8)));

        assertThrows(
                PaymentGatewayException.class,
                () -> service.verify(7L, "CHARGENOW", request, "{\"eventId\":\"evt-2\"}"));
    }

    @Test
    void replayedNonceIsRejectedEvenWithValidHmac() throws Exception {
        var configurations = mock(VendingConnectorConfigurationService.class);
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract());
        when(jdbc.update(any(String.class), any(MapSqlParameterSource.class)))
                .thenThrow(new DuplicateKeyException("duplicate nonce"));
        var service = new VendingCallbackSecurityService(configurations, jdbc);

        String body = "{\"eventId\":\"evt-3\"}";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = "nonce-replayed";
        var request = request(timestamp, nonce, sign(timestamp + "\n" + nonce + "\n" + body));

        assertThrows(
                PaymentGatewayException.class,
                () -> service.verify(7L, "CHARGENOW", request, body));
    }

    @Test
    void staleTimestampIsRejected() throws Exception {
        var configurations = mock(VendingConnectorConfigurationService.class);
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        when(configurations.require(7L, "CHARGENOW")).thenReturn(contract());
        var service = new VendingCallbackSecurityService(configurations, jdbc);

        String timestamp = String.valueOf(Instant.now().minusSeconds(600).getEpochSecond());
        String nonce = "nonce-stale";
        String body = "{}";
        var request = request(timestamp, nonce, sign(timestamp + "\n" + nonce + "\n" + body));

        assertThrows(
                PaymentGatewayException.class,
                () -> service.verify(7L, "CHARGENOW", request, body));
    }

    private MockHttpServletRequest request(String timestamp, String nonce, String signature) {
        var request = new MockHttpServletRequest();
        request.addHeader("X-CPay-Vending-Signature", signature);
        request.addHeader("X-CPay-Vending-Timestamp", timestamp);
        request.addHeader("X-CPay-Vending-Nonce", nonce);
        return request;
    }

    private String sign(String base) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(base.getBytes(StandardCharsets.UTF_8)));
    }

    private Contract contract() {
        return new Contract(
                7L,
                "CHARGENOW",
                "https://manufacturer.example",
                "/release",
                "{}",
                "NONE",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                SECRET,
                "HMAC_SHA256_TS_NONCE_BODY",
                "X-CPay-Vending-Signature",
                "X-CPay-Vending-Timestamp",
                "X-CPay-Vending-Nonce",
                "eventType",
                "eventId",
                "deviceId",
                "rentalReference",
                "assetCode",
                "availableCount");
    }
}
