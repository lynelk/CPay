package net.citotech.cito.vending.connector;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.admin.TenantScopeGuard;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-scoped manufacturer contract store for vending connectors.
 *
 * <p>The vendor's endpoint/auth/payload contract is data, not guessed source code. Secrets are
 * encrypted with the same AES-GCM service used for merchant channel credentials and are never
 * returned from list/get operations.
 */
@Service
public class VendingConnectorConfigurationService {
    private final NamedParameterJdbcTemplate jdbc;
    private final MerchantChannelCryptoService crypto;
    private final SecureRandom random = new SecureRandom();

    public VendingConnectorConfigurationService(
            NamedParameterJdbcTemplate jdbc, MerchantChannelCryptoService crypto) {
        this.jdbc = jdbc;
        this.crypto = crypto;
    }

    public List<Map<String, Object>> list(long merchantId) {
        String sql =
                "SELECT id, merchant_id, connector_code, command_base_url, release_path, auth_mode, "
                        + "auth_header_name, response_success_field, response_success_value, "
                        + "response_reference_field, response_message_field, callback_signature_mode, "
                        + "callback_signature_header, callback_timestamp_header, callback_nonce_header, "
                        + "callback_event_type_field, callback_event_id_field, callback_device_field, "
                        + "callback_rental_field, callback_asset_field, callback_available_count_field, "
                        + "active_flag, created_at, updated_at, "
                        + "CASE WHEN auth_value_ciphertext IS NULL THEN 'NO' ELSE 'YES' END AS auth_value_configured, "
                        + "CASE WHEN auth_secret_ciphertext IS NULL THEN 'NO' ELSE 'YES' END AS auth_secret_configured, "
                        + "CASE WHEN callback_secret_ciphertext IS NULL THEN 'NO' ELSE 'YES' END AS callback_secret_configured "
                        + "FROM vending_connector_configs WHERE merchant_id=:tenant_merchant_id ORDER BY connector_code";
        TenantScopeGuard.assertTenantBound(sql);
        return jdbc.queryForList(sql, TenantScopeGuard.scope(null, merchantId));
    }

    public Map<String, Object> view(long merchantId, String connectorCode) {
        String normalized = normalize(connectorCode);
        return list(merchantId).stream()
                .filter(row -> normalized.equals(normalize(String.valueOf(row.get("connector_code")))))
                .findFirst()
                .orElseThrow(() -> new PaymentGatewayException("Vending connector configuration was not found"));
    }

    public Contract require(long merchantId, String connectorCode) {
        String sql =
                "SELECT * FROM vending_connector_configs WHERE merchant_id=:tenant_merchant_id "
                        + "AND connector_code=:connector_code AND active_flag='YES' LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", normalize(connectorCode));
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "Active vending connector contract is not configured: " + connectorCode);
        }
        Map<String, Object> row = rows.get(0);
        return new Contract(
                merchantId,
                normalize(String.valueOf(row.get("connector_code"))),
                text(row.get("command_base_url")),
                text(row.get("release_path")),
                text(row.get("release_request_template")),
                normalize(text(row.get("auth_mode"))),
                text(row.get("auth_header_name")),
                decryptNullable(row.get("auth_value_ciphertext")),
                decryptNullable(row.get("auth_secret_ciphertext")),
                text(row.get("response_success_field")),
                text(row.get("response_success_value")),
                text(row.get("response_reference_field")),
                text(row.get("response_message_field")),
                decryptRequired(row.get("callback_secret_ciphertext"), "callback secret"),
                normalize(text(row.get("callback_signature_mode"))),
                text(row.get("callback_signature_header")),
                text(row.get("callback_timestamp_header")),
                text(row.get("callback_nonce_header")),
                text(row.get("callback_event_type_field")),
                text(row.get("callback_event_id_field")),
                text(row.get("callback_device_field")),
                text(row.get("callback_rental_field")),
                text(row.get("callback_asset_field")),
                text(row.get("callback_available_count_field")));
    }

    @Transactional
    public Map<String, Object> save(long merchantId, String connectorCode, Map<String, Object> body) {
        String code = normalize(connectorCode);
        String baseUrl = required(body.get("commandBaseUrl"), "commandBaseUrl");
        String releasePath = required(body.get("releasePath"), "releasePath");
        String releaseTemplate = required(body.get("releaseRequestTemplate"), "releaseRequestTemplate");
        validateBaseUrl(baseUrl);

        String callbackSecret = text(body.get("callbackSecret"));
        String authValue = text(body.get("authValue"));
        String authSecret = text(body.get("authSecret"));
        ExistingSecrets existing = existingSecrets(merchantId, code);
        String callbackCipher =
                callbackSecret.isBlank()
                        ? existing.callbackSecretCiphertext()
                        : crypto.encrypt(callbackSecret);
        if (callbackCipher == null || callbackCipher.isBlank()) {
            throw new PaymentGatewayException("callbackSecret is required for an active manufacturer connector");
        }
        String authValueCipher =
                authValue.isBlank() ? existing.authValueCiphertext() : crypto.encrypt(authValue);
        String authSecretCipher =
                authSecret.isBlank() ? existing.authSecretCiphertext() : crypto.encrypt(authSecret);

        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", code);
        p.addValue("command_base_url", baseUrl);
        p.addValue("release_path", releasePath);
        p.addValue("release_request_template", releaseTemplate);
        p.addValue("auth_mode", normalize(defaulted(body.get("authMode"), "BEARER")));
        p.addValue("auth_header_name", blankToNull(text(body.get("authHeaderName"))));
        p.addValue("auth_value_ciphertext", authValueCipher);
        p.addValue("auth_secret_ciphertext", authSecretCipher);
        p.addValue("response_success_field", blankToNull(text(body.get("responseSuccessField"))));
        p.addValue("response_success_value", blankToNull(text(body.get("responseSuccessValue"))));
        p.addValue("response_reference_field", blankToNull(text(body.get("responseReferenceField"))));
        p.addValue("response_message_field", blankToNull(text(body.get("responseMessageField"))));
        p.addValue("callback_secret_ciphertext", callbackCipher);
        p.addValue(
                "callback_signature_mode",
                normalize(defaulted(body.get("callbackSignatureMode"), "HMAC_SHA256_TS_NONCE_BODY")));
        p.addValue(
                "callback_signature_header",
                defaulted(body.get("callbackSignatureHeader"), "X-CPay-Vending-Signature"));
        p.addValue(
                "callback_timestamp_header",
                defaulted(body.get("callbackTimestampHeader"), "X-CPay-Vending-Timestamp"));
        p.addValue(
                "callback_nonce_header",
                defaulted(body.get("callbackNonceHeader"), "X-CPay-Vending-Nonce"));
        p.addValue("callback_event_type_field", defaulted(body.get("callbackEventTypeField"), "eventType"));
        p.addValue("callback_event_id_field", defaulted(body.get("callbackEventIdField"), "eventId"));
        p.addValue("callback_device_field", defaulted(body.get("callbackDeviceField"), "deviceId"));
        p.addValue("callback_rental_field", blankToNull(text(body.get("callbackRentalField"))));
        p.addValue("callback_asset_field", blankToNull(text(body.get("callbackAssetField"))));
        p.addValue(
                "callback_available_count_field",
                blankToNull(text(body.get("callbackAvailableCountField"))));
        p.addValue("active_flag", yesNo(body.get("active"), true));

        String sql =
                "INSERT INTO vending_connector_configs (merchant_id, connector_code, command_base_url, "
                        + "release_path, release_request_template, auth_mode, auth_header_name, auth_value_ciphertext, "
                        + "auth_secret_ciphertext, response_success_field, response_success_value, response_reference_field, "
                        + "response_message_field, callback_secret_ciphertext, callback_signature_mode, "
                        + "callback_signature_header, callback_timestamp_header, callback_nonce_header, "
                        + "callback_event_type_field, callback_event_id_field, callback_device_field, callback_rental_field, "
                        + "callback_asset_field, callback_available_count_field, active_flag) VALUES "
                        + "(:tenant_merchant_id, :connector_code, :command_base_url, :release_path, :release_request_template, "
                        + ":auth_mode, :auth_header_name, :auth_value_ciphertext, :auth_secret_ciphertext, :response_success_field, "
                        + ":response_success_value, :response_reference_field, :response_message_field, :callback_secret_ciphertext, "
                        + ":callback_signature_mode, :callback_signature_header, :callback_timestamp_header, :callback_nonce_header, "
                        + ":callback_event_type_field, :callback_event_id_field, :callback_device_field, :callback_rental_field, "
                        + ":callback_asset_field, :callback_available_count_field, :active_flag) "
                        + "ON DUPLICATE KEY UPDATE command_base_url=VALUES(command_base_url), release_path=VALUES(release_path), "
                        + "release_request_template=VALUES(release_request_template), auth_mode=VALUES(auth_mode), "
                        + "auth_header_name=VALUES(auth_header_name), auth_value_ciphertext=VALUES(auth_value_ciphertext), "
                        + "auth_secret_ciphertext=VALUES(auth_secret_ciphertext), response_success_field=VALUES(response_success_field), "
                        + "response_success_value=VALUES(response_success_value), response_reference_field=VALUES(response_reference_field), "
                        + "response_message_field=VALUES(response_message_field), callback_secret_ciphertext=VALUES(callback_secret_ciphertext), "
                        + "callback_signature_mode=VALUES(callback_signature_mode), callback_signature_header=VALUES(callback_signature_header), "
                        + "callback_timestamp_header=VALUES(callback_timestamp_header), callback_nonce_header=VALUES(callback_nonce_header), "
                        + "callback_event_type_field=VALUES(callback_event_type_field), callback_event_id_field=VALUES(callback_event_id_field), "
                        + "callback_device_field=VALUES(callback_device_field), callback_rental_field=VALUES(callback_rental_field), "
                        + "callback_asset_field=VALUES(callback_asset_field), callback_available_count_field=VALUES(callback_available_count_field), "
                        + "active_flag=VALUES(active_flag)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
        return view(merchantId, code);
    }

    /** Generates a strong callback secret, stores only its ciphertext, and returns cleartext once. */
    @Transactional
    public Map<String, Object> rotateCallbackSecret(long merchantId, String connectorCode) {
        String code = normalize(connectorCode);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String sql =
                "UPDATE vending_connector_configs SET callback_secret_ciphertext=:secret "
                        + "WHERE merchant_id=:tenant_merchant_id AND connector_code=:connector_code";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", code);
        p.addValue("secret", crypto.encrypt(secret));
        if (jdbc.update(sql, p) == 0) {
            throw new PaymentGatewayException("Vending connector configuration was not found");
        }
        return Map.of("connectorCode", code, "callbackSecret", secret);
    }

    private ExistingSecrets existingSecrets(long merchantId, String code) {
        String sql =
                "SELECT auth_value_ciphertext, auth_secret_ciphertext, callback_secret_ciphertext "
                        + "FROM vending_connector_configs WHERE merchant_id=:tenant_merchant_id AND connector_code=:connector_code";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", code);
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) return new ExistingSecrets(null, null, null);
        Map<String, Object> row = rows.get(0);
        return new ExistingSecrets(
                textOrNull(row.get("auth_value_ciphertext")),
                textOrNull(row.get("auth_secret_ciphertext")),
                textOrNull(row.get("callback_secret_ciphertext")));
    }

    private String decryptNullable(Object cipher) {
        String value = text(cipher);
        return value.isBlank() ? "" : crypto.decrypt(value);
    }

    private String decryptRequired(Object cipher, String name) {
        String value = decryptNullable(cipher);
        if (value.isBlank()) throw new PaymentGatewayException("Vending connector " + name + " is missing");
        return value;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String required(Object value, String name) {
        String result = text(value);
        if (result.isBlank()) throw new PaymentGatewayException(name + " is required");
        return result;
    }

    private String defaulted(Object value, String fallback) {
        String result = text(value);
        return result.isBlank() ? fallback : result;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String textOrNull(Object value) {
        String text = text(value);
        return text.isBlank() ? null : text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String yesNo(Object value, boolean fallback) {
        if (value == null) return fallback ? "YES" : "NO";
        if (value instanceof Boolean b) return b ? "YES" : "NO";
        String raw = text(value);
        if (raw.isBlank()) return fallback ? "YES" : "NO";
        return ("YES".equalsIgnoreCase(raw) || "TRUE".equalsIgnoreCase(raw) || "1".equals(raw))
                ? "YES"
                : "NO";
    }

    private void validateBaseUrl(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("https://")
                && !normalized.startsWith("http://localhost")
                && !normalized.startsWith("http://127.0.0.1")) {
            throw new PaymentGatewayException(
                    "Manufacturer commandBaseUrl must use HTTPS (localhost is allowed for sandbox testing)");
        }
    }

    private record ExistingSecrets(
            String authValueCiphertext, String authSecretCiphertext, String callbackSecretCiphertext) {}

    public record Contract(
            long merchantId,
            String connectorCode,
            String commandBaseUrl,
            String releasePath,
            String releaseRequestTemplate,
            String authMode,
            String authHeaderName,
            String authValue,
            String authSecret,
            String responseSuccessField,
            String responseSuccessValue,
            String responseReferenceField,
            String responseMessageField,
            String callbackSecret,
            String callbackSignatureMode,
            String callbackSignatureHeader,
            String callbackTimestampHeader,
            String callbackNonceHeader,
            String callbackEventTypeField,
            String callbackEventIdField,
            String callbackDeviceField,
            String callbackRentalField,
            String callbackAssetField,
            String callbackAvailableCountField) {}
}
