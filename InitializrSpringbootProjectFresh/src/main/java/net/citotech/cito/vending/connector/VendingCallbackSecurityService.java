package net.citotech.cito.vending.connector;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.vending.connector.VendingConnectorConfigurationService.Contract;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/** Verifies manufacturer callbacks before any device/rental state is changed. */
@Service
public class VendingCallbackSecurityService {
    private static final long MAX_SKEW_SECONDS = 300;
    private final VendingConnectorConfigurationService configurations;
    private final NamedParameterJdbcTemplate jdbc;

    public VendingCallbackSecurityService(
            VendingConnectorConfigurationService configurations, NamedParameterJdbcTemplate jdbc) {
        this.configurations = configurations;
        this.jdbc = jdbc;
    }

    public Contract verify(
            long merchantId, String connectorCode, HttpServletRequest request, String rawBody) {
        Contract contract = configurations.require(merchantId, connectorCode);
        String signature = requiredHeader(request, contract.callbackSignatureHeader());
        String timestamp = requiredHeader(request, contract.callbackTimestampHeader());
        String nonce = requiredHeader(request, contract.callbackNonceHeader());
        if (nonce.length() > 160) throw new PaymentGatewayException("Vending callback nonce is too long");
        Instant sentAt = parseTimestamp(timestamp);
        long skew = Math.abs(Instant.now().getEpochSecond() - sentAt.getEpochSecond());
        if (skew > MAX_SKEW_SECONDS) {
            throw new PaymentGatewayException("Vending callback timestamp is outside the allowed window");
        }

        String mode = contract.callbackSignatureMode();
        String base;
        if ("HMAC_SHA256_BODY".equals(mode)) {
            base = rawBody == null ? "" : rawBody;
        } else if ("HMAC_SHA256_TS_NONCE_BODY".equals(mode)) {
            base = timestamp + "\n" + nonce + "\n" + (rawBody == null ? "" : rawBody);
        } else {
            throw new PaymentGatewayException("Unsupported vending callback signature mode: " + mode);
        }
        String expected = hmac(contract.callbackSecret(), base);
        if (!constantTimeSignatureEquals(expected, signature)) {
            throw new PaymentGatewayException("Vending callback signature verification failed");
        }
        claimNonce(merchantId, contract.connectorCode(), nonce);
        return contract;
    }

    private void claimNonce(long merchantId, String connectorCode, String nonce) {
        String sql =
                "INSERT INTO vending_callback_nonces (merchant_id, connector_code, nonce_value) "
                        + "VALUES (:tenant_merchant_id, :connector_code, :nonce)";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", connectorCode);
        p.addValue("nonce", nonce);
        try {
            jdbc.update(sql, p);
        } catch (DuplicateKeyException e) {
            throw new PaymentGatewayException("Vending callback nonce has already been used");
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException e) {
                throw new PaymentGatewayException("Vending callback timestamp is invalid");
            }
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        if (name == null || name.isBlank()) throw new PaymentGatewayException("Callback header mapping is missing");
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Missing vending callback header: " + name);
        }
        return value.trim();
    }

    private String hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to verify vending callback signature", e);
        }
    }

    private boolean constantTimeSignatureEquals(String expectedBase64, String supplied) {
        String normalized = supplied == null ? "" : supplied.trim();
        if (normalized.regionMatches(true, 0, "sha256=", 0, 7)) normalized = normalized.substring(7);
        try {
            byte[] expected = Base64.getDecoder().decode(expectedBase64);
            byte[] actual = Base64.getDecoder().decode(normalized);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ignored) {
            return MessageDigest.isEqual(
                    expectedBase64.getBytes(StandardCharsets.UTF_8),
                    normalized.getBytes(StandardCharsets.UTF_8));
        }
    }
}
