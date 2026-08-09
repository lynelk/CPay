package net.citotech.cito.vending.connector;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
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
        String mode = contract.callbackSignatureMode();
        String signature = requiredHeader(request, contract.callbackSignatureHeader());

        if ("STATIC_TOKEN_HEADER".equals(mode)) {
            if (!constantTimeTextEquals(contract.callbackSecret(), signature)) {
                throw new PaymentGatewayException(
                        "Vending callback authentication token verification failed");
            }
            return contract;
        }

        String timestamp = "";
        String nonce = "";
        String base;
        if ("HMAC_SHA256_BODY".equals(mode)) {
            base = body(rawBody);
        } else if ("HMAC_SHA256_TS_BODY".equals(mode)) {
            timestamp = verifiedTimestamp(request, contract.callbackTimestampHeader());
            base = timestamp + "\n" + body(rawBody);
        } else if ("HMAC_SHA256_TS_NONCE_BODY".equals(mode)) {
            timestamp = verifiedTimestamp(request, contract.callbackTimestampHeader());
            nonce = requiredHeader(request, contract.callbackNonceHeader());
            if (nonce.length() > 160) {
                throw new PaymentGatewayException("Vending callback nonce is too long");
            }
            base = timestamp + "\n" + nonce + "\n" + body(rawBody);
        } else {
            throw new PaymentGatewayException(
                    "Unsupported vending callback signature mode: " + mode);
        }

        byte[] expected = hmac(contract.callbackSecret(), base);
        if (!constantTimeSignatureEquals(
                expected, signature, contract.callbackSignatureEncoding())) {
            throw new PaymentGatewayException(
                    "Vending callback signature verification failed");
        }
        if (!nonce.isBlank()) claimNonce(merchantId, contract.connectorCode(), nonce);
        return contract;
    }

    private String verifiedTimestamp(HttpServletRequest request, String header) {
        String timestamp = requiredHeader(request, header);
        Instant sentAt = parseTimestamp(timestamp);
        long skew = Math.abs(Instant.now().getEpochSecond() - sentAt.getEpochSecond());
        if (skew > MAX_SKEW_SECONDS) {
            throw new PaymentGatewayException(
                    "Vending callback timestamp is outside the allowed window");
        }
        return timestamp;
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
            throw new PaymentGatewayException(
                    "Vending callback nonce has already been used");
        }
    }

    private Instant parseTimestamp(String value) {
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException e) {
                throw new PaymentGatewayException(
                        "Vending callback timestamp is invalid");
            }
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        if (name == null || name.isBlank()) {
            throw new PaymentGatewayException("Callback header mapping is missing");
        }
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException("Missing vending callback header: " + name);
        }
        return value.trim();
    }

    private byte[] hmac(String secret, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(
                    new SecretKeySpec(
                            secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to verify vending callback signature", e);
        }
    }

    private boolean constantTimeSignatureEquals(
            byte[] expected, String supplied, String encoding) {
        String normalized = supplied == null ? "" : supplied.trim();
        if (normalized.regionMatches(true, 0, "sha256=", 0, 7)) {
            normalized = normalized.substring(7);
        }
        try {
            byte[] actual =
                    "HEX".equalsIgnoreCase(encoding)
                            ? HexFormat.of().parseHex(normalized)
                            : Base64.getDecoder().decode(normalized);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean constantTimeTextEquals(String expected, String supplied) {
        return MessageDigest.isEqual(
                body(expected).getBytes(StandardCharsets.UTF_8),
                body(supplied).getBytes(StandardCharsets.UTF_8));
    }

    private String body(String value) {
        return value == null ? "" : value;
    }
}
