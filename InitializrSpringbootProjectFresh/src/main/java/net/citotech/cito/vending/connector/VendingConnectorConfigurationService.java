package net.citotech.cito.vending.connector;

import java.net.URI;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 * <p>The OEM endpoint/auth/payload contract is data, not guessed source code. Secrets are encrypted
 * with the same AES-GCM service used for merchant channel credentials and are never returned from
 * list/get operations. V52 also stores operation-specific mappings so ChargeNow can expose release,
 * status, diagnostics and future commands without another Java adapter per endpoint.
 */
@Service
public class VendingConnectorConfigurationService {
    private static final Set<String> AUTH_MODES =
            Set.of("NONE", "BEARER", "API_KEY_HEADER", "BASIC", "HMAC_SHA256_TS_BODY");
    private static final Set<String> CALLBACK_MODES =
            Set.of(
                    "HMAC_SHA256_TS_NONCE_BODY",
                    "HMAC_SHA256_TS_BODY",
                    "HMAC_SHA256_BODY",
                    "STATIC_TOKEN_HEADER");
    private static final Set<String> ENCODINGS = Set.of("BASE64", "HEX");
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> COMPLETION_MODES = Set.of("IMMEDIATE", "CALLBACK");

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
                        + "auth_header_name, auth_timestamp_header, auth_key_header, auth_signature_encoding, "
                        + "response_success_field, response_success_value, response_reference_field, response_message_field, "
                        + "callback_signature_mode, callback_signature_encoding, callback_signature_header, "
                        + "callback_timestamp_header, callback_nonce_header, callback_event_type_field, callback_event_id_field, "
                        + "callback_device_field, callback_rental_field, callback_asset_field, callback_available_count_field, "
                        + "callback_heartbeat_value, callback_return_value, callback_release_value, callback_offline_value, "
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
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "Vending connector configuration was not found"));
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
                normalize(text(row.get("auth_mode"))),
                text(row.get("auth_header_name")),
                text(row.get("auth_timestamp_header")),
                text(row.get("auth_key_header")),
                normalize(defaulted(row.get("auth_signature_encoding"), "BASE64")),
                text(row.get("auth_signing_template")),
                decryptNullable(row.get("auth_value_ciphertext")),
                decryptNullable(row.get("auth_secret_ciphertext")),
                decryptRequired(row.get("callback_secret_ciphertext"), "callback secret"),
                normalize(text(row.get("callback_signature_mode"))),
                normalize(defaulted(row.get("callback_signature_encoding"), "BASE64")),
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

    public List<Map<String, Object>> operations(long merchantId, String connectorCode) {
        String sql =
                "SELECT id, merchant_id, connector_code, command_type, http_method, command_path, request_template, "
                        + "idempotency_header_name, response_success_field, response_success_value, response_reference_field, "
                        + "response_message_field, completion_mode, active_flag, created_at, updated_at "
                        + "FROM vending_connector_operations WHERE merchant_id=:tenant_merchant_id "
                        + "AND connector_code=:connector_code ORDER BY command_type";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", normalize(connectorCode));
        return jdbc.queryForList(sql, p);
    }

    public Operation requireOperation(long merchantId, String connectorCode, String commandType) {
        String sql =
                "SELECT * FROM vending_connector_operations WHERE merchant_id=:tenant_merchant_id "
                        + "AND connector_code=:connector_code AND command_type=:command_type "
                        + "AND active_flag='YES' LIMIT 1";
        TenantScopeGuard.assertTenantBound(sql);
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", normalize(connectorCode));
        p.addValue("command_type", normalize(commandType));
        List<Map<String, Object>> rows = jdbc.queryForList(sql, p);
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "Manufacturer operation is not configured: " + normalize(commandType));
        }
        Map<String, Object> row = rows.get(0);
        return new Operation(
                normalize(text(row.get("command_type"))),
                normalize(text(row.get("http_method"))),
                text(row.get("command_path")),
                text(row.get("request_template")),
                text(row.get("idempotency_header_name")),
                text(row.get("response_success_field")),
                text(row.get("response_success_value")),
                text(row.get("response_reference_field")),
                text(row.get("response_message_field")),
                normalize(defaulted(row.get("completion_mode"), "CALLBACK")));
    }

    @Transactional
    public Map<String, Object> save(long merchantId, String connectorCode, Map<String, Object> body) {
        String code = normalize(connectorCode);
        String baseUrl = required(body.get("commandBaseUrl"), "commandBaseUrl");
        String releasePath = required(body.get("releasePath"), "releasePath");
        String releaseTemplate =
                required(body.get("releaseRequestTemplate"), "releaseRequestTemplate");
        validateBaseUrl(baseUrl);

        String authMode = allowed(body.get("authMode"), "BEARER", AUTH_MODES, "authMode");
        String authEncoding =
                allowed(
                        body.get("authSignatureEncoding"),
                        "BASE64",
                        ENCODINGS,
                        "authSignatureEncoding");
        String callbackMode =
                allowed(
                        body.get("callbackSignatureMode"),
                        "HMAC_SHA256_TS_NONCE_BODY",
                        CALLBACK_MODES,
                        "callbackSignatureMode");
        String callbackEncoding =
                allowed(
                        body.get("callbackSignatureEncoding"),
                        "BASE64",
                        ENCODINGS,
                        "callbackSignatureEncoding");

        String callbackSecret = text(body.get("callbackSecret"));
        String authValue = text(body.get("authValue"));
        String authSecret = text(body.get("authSecret"));
        ExistingSecrets existing = existingSecrets(merchantId, code);
        String callbackCipher =
                callbackSecret.isBlank()
                        ? existing.callbackSecretCiphertext()
                        : crypto.encrypt(callbackSecret);
        if (callbackCipher == null || callbackCipher.isBlank()) {
            throw new PaymentGatewayException(
                    "callbackSecret is required for an active manufacturer connector");
        }
        String authValueCipher =
                authValue.isBlank() ? existing.authValueCiphertext() : crypto.encrypt(authValue);
        String authSecretCipher =
                authSecret.isBlank() ? existing.authSecretCiphertext() : crypto.encrypt(authSecret);

        validateAuthSecrets(authMode, authValueCipher, authSecretCipher);

        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", code);
        p.addValue("command_base_url", baseUrl);
        p.addValue("release_path", releasePath);
        p.addValue("release_request_template", releaseTemplate);
        p.addValue("auth_mode", authMode);
        p.addValue("auth_header_name", blankToNull(text(body.get("authHeaderName"))));
        p.addValue("auth_timestamp_header", blankToNull(text(body.get("authTimestampHeader"))));
        p.addValue("auth_key_header", blankToNull(text(body.get("authKeyHeader"))));
        p.addValue("auth_signature_encoding", authEncoding);
        p.addValue("auth_signing_template", blankToNull(text(body.get("authSigningTemplate"))));
        p.addValue("auth_value_ciphertext", authValueCipher);
        p.addValue("auth_secret_ciphertext", authSecretCipher);
        p.addValue("response_success_field", blankToNull(text(body.get("responseSuccessField"))));
        p.addValue("response_success_value", blankToNull(text(body.get("responseSuccessValue"))));
        p.addValue("response_reference_field", blankToNull(text(body.get("responseReferenceField"))));
        p.addValue("response_message_field", blankToNull(text(body.get("responseMessageField"))));
        p.addValue("callback_secret_ciphertext", callbackCipher);
        p.addValue("callback_signature_mode", callbackMode);
        p.addValue("callback_signature_encoding", callbackEncoding);
        p.addValue(
                "callback_signature_header",
                defaulted(body.get("callbackSignatureHeader"), "X-CPay-Vending-Signature"));
        p.addValue(
                "callback_timestamp_header",
                defaulted(body.get("callbackTimestampHeader"), "X-CPay-Vending-Timestamp"));
        p.addValue(
                "callback_nonce_header",
                defaulted(body.get("callbackNonceHeader"), "X-CPay-Vending-Nonce"));
        p.addValue(
                "callback_event_type_field",
                defaulted(body.get("callbackEventTypeField"), "eventType"));
        p.addValue(
                "callback_event_id_field",
                defaulted(body.get("callbackEventIdField"), "eventId"));
        p.addValue(
                "callback_device_field",
                defaulted(body.get("callbackDeviceField"), "deviceId"));
        p.addValue("callback_rental_field", blankToNull(text(body.get("callbackRentalField"))));
        p.addValue("callback_asset_field", blankToNull(text(body.get("callbackAssetField"))));
        p.addValue(
                "callback_available_count_field",
                blankToNull(text(body.get("callbackAvailableCountField"))));
        p.addValue(
                "callback_heartbeat_value",
                defaulted(body.get("callbackHeartbeatValue"), "HEARTBEAT"));
        p.addValue(
                "callback_return_value",
                defaulted(body.get("callbackReturnValue"), "ASSET_RETURNED"));
        p.addValue(
                "callback_release_value",
                defaulted(body.get("callbackReleaseValue"), "ASSET_RELEASED"));
        p.addValue(
                "callback_offline_value",
                defaulted(body.get("callbackOfflineValue"), "DEVICE_OFFLINE"));
        p.addValue("active_flag", yesNo(body.get("active"), true));

        String sql =
                "INSERT INTO vending_connector_configs (merchant_id, connector_code, command_base_url, release_path, "
                        + "release_request_template, auth_mode, auth_header_name, auth_timestamp_header, auth_key_header, "
                        + "auth_signature_encoding, auth_signing_template, auth_value_ciphertext, auth_secret_ciphertext, "
                        + "response_success_field, response_success_value, response_reference_field, response_message_field, "
                        + "callback_secret_ciphertext, callback_signature_mode, callback_signature_encoding, callback_signature_header, "
                        + "callback_timestamp_header, callback_nonce_header, callback_event_type_field, callback_event_id_field, "
                        + "callback_device_field, callback_rental_field, callback_asset_field, callback_available_count_field, "
                        + "callback_heartbeat_value, callback_return_value, callback_release_value, callback_offline_value, active_flag) VALUES "
                        + "(:tenant_merchant_id, :connector_code, :command_base_url, :release_path, :release_request_template, "
                        + ":auth_mode, :auth_header_name, :auth_timestamp_header, :auth_key_header, :auth_signature_encoding, "
                        + ":auth_signing_template, :auth_value_ciphertext, :auth_secret_ciphertext, :response_success_field, "
                        + ":response_success_value, :response_reference_field, :response_message_field, :callback_secret_ciphertext, "
                        + ":callback_signature_mode, :callback_signature_encoding, :callback_signature_header, :callback_timestamp_header, "
                        + ":callback_nonce_header, :callback_event_type_field, :callback_event_id_field, :callback_device_field, "
                        + ":callback_rental_field, :callback_asset_field, :callback_available_count_field, :callback_heartbeat_value, "
                        + ":callback_return_value, :callback_release_value, :callback_offline_value, :active_flag) "
                        + "ON DUPLICATE KEY UPDATE command_base_url=VALUES(command_base_url), release_path=VALUES(release_path), "
                        + "release_request_template=VALUES(release_request_template), auth_mode=VALUES(auth_mode), "
                        + "auth_header_name=VALUES(auth_header_name), auth_timestamp_header=VALUES(auth_timestamp_header), "
                        + "auth_key_header=VALUES(auth_key_header), auth_signature_encoding=VALUES(auth_signature_encoding), "
                        + "auth_signing_template=VALUES(auth_signing_template), auth_value_ciphertext=VALUES(auth_value_ciphertext), "
                        + "auth_secret_ciphertext=VALUES(auth_secret_ciphertext), response_success_field=VALUES(response_success_field), "
                        + "response_success_value=VALUES(response_success_value), response_reference_field=VALUES(response_reference_field), "
                        + "response_message_field=VALUES(response_message_field), callback_secret_ciphertext=VALUES(callback_secret_ciphertext), "
                        + "callback_signature_mode=VALUES(callback_signature_mode), callback_signature_encoding=VALUES(callback_signature_encoding), "
                        + "callback_signature_header=VALUES(callback_signature_header), callback_timestamp_header=VALUES(callback_timestamp_header), "
                        + "callback_nonce_header=VALUES(callback_nonce_header), callback_event_type_field=VALUES(callback_event_type_field), "
                        + "callback_event_id_field=VALUES(callback_event_id_field), callback_device_field=VALUES(callback_device_field), "
                        + "callback_rental_field=VALUES(callback_rental_field), callback_asset_field=VALUES(callback_asset_field), "
                        + "callback_available_count_field=VALUES(callback_available_count_field), callback_heartbeat_value=VALUES(callback_heartbeat_value), "
                        + "callback_return_value=VALUES(callback_return_value), callback_release_value=VALUES(callback_release_value), "
                        + "callback_offline_value=VALUES(callback_offline_value), active_flag=VALUES(active_flag)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);

        Map<String, Object> release = new LinkedHashMap<>();
        release.put("httpMethod", "POST");
        release.put("commandPath", releasePath);
        release.put("requestTemplate", releaseTemplate);
        release.put("idempotencyHeaderName", text(body.get("idempotencyHeaderName")));
        release.put("responseSuccessField", text(body.get("responseSuccessField")));
        release.put("responseSuccessValue", text(body.get("responseSuccessValue")));
        release.put("responseReferenceField", text(body.get("responseReferenceField")));
        release.put("responseMessageField", text(body.get("responseMessageField")));
        release.put("completionMode", defaulted(body.get("releaseCompletionMode"), "CALLBACK"));
        release.put("active", body.getOrDefault("active", Boolean.TRUE));
        saveOperationInternal(merchantId, code, "RELEASE_ASSET", release);
        return view(merchantId, code);
    }

    @Transactional
    public Map<String, Object> saveOperation(
            long merchantId, String connectorCode, String commandType, Map<String, Object> body) {
        // A connector must exist before operation mappings can be added, so credentials and
        // callback policy always have one tenant-owned parent contract.
        require(merchantId, connectorCode);
        saveOperationInternal(merchantId, normalize(connectorCode), commandType, body);
        return operations(merchantId, connectorCode).stream()
                .filter(
                        row ->
                                normalize(commandType)
                                        .equals(normalize(text(row.get("command_type")))))
                .findFirst()
                .orElseThrow();
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

    /**
     * Reports whether CPay has enough contract data to enter OEM sandbox certification. It does not
     * claim that the OEM endpoint itself has been certified, because that requires partner
     * credentials and physical/sandbox hardware.
     */
    public Map<String, Object> readiness(long merchantId, String connectorCode) {
        List<String> issues = new ArrayList<>();
        Contract contract;
        try {
            contract = require(merchantId, connectorCode);
        } catch (PaymentGatewayException e) {
            return Map.of(
                    "connectorCode",
                    normalize(connectorCode),
                    "status",
                    "NOT_CONFIGURED",
                    "readyForSandbox",
                    false,
                    "issues",
                    List.of(e.getMessage()));
        }
        try {
            requireOperation(merchantId, connectorCode, "RELEASE_ASSET");
        } catch (PaymentGatewayException e) {
            issues.add("RELEASE_ASSET operation is not configured");
        }
        if ("NONE".equals(contract.authMode()) && !isLocalSandbox(contract.commandBaseUrl())) {
            issues.add("Outbound authentication cannot be NONE for a non-local OEM endpoint");
        }
        if (!AUTH_MODES.contains(contract.authMode())) issues.add("Unsupported outbound authentication mode");
        if (!CALLBACK_MODES.contains(contract.callbackSignatureMode())) {
            issues.add("Unsupported callback authentication mode");
        }
        if (contract.callbackEventTypeField().isBlank()
                || contract.callbackEventIdField().isBlank()
                || contract.callbackDeviceField().isBlank()) {
            issues.add("Callback event type, event id and device field mappings are required");
        }
        return Map.of(
                "connectorCode",
                contract.connectorCode(),
                "status",
                issues.isEmpty() ? "READY_FOR_OEM_SANDBOX" : "INCOMPLETE",
                "readyForSandbox",
                issues.isEmpty(),
                "operationCount",
                operations(merchantId, connectorCode).size(),
                "issues",
                issues);
    }

    private void saveOperationInternal(
            long merchantId, String connectorCode, String commandType, Map<String, Object> body) {
        String code = normalize(connectorCode);
        String type = normalize(required(commandType, "commandType"));
        String method = allowed(body.get("httpMethod"), "POST", HTTP_METHODS, "httpMethod");
        String path = required(body.get("commandPath"), "commandPath");
        String completion =
                allowed(
                        body.get("completionMode"),
                        "CALLBACK",
                        COMPLETION_MODES,
                        "completionMode");
        MapSqlParameterSource p = TenantScopeGuard.scope(null, merchantId);
        p.addValue("connector_code", code);
        p.addValue("command_type", type);
        p.addValue("http_method", method);
        p.addValue("command_path", path);
        p.addValue("request_template", blankToNull(text(body.get("requestTemplate"))));
        p.addValue(
                "idempotency_header_name",
                blankToNull(text(body.get("idempotencyHeaderName"))));
        p.addValue("response_success_field", blankToNull(text(body.get("responseSuccessField"))));
        p.addValue("response_success_value", blankToNull(text(body.get("responseSuccessValue"))));
        p.addValue(
                "response_reference_field",
                blankToNull(text(body.get("responseReferenceField"))));
        p.addValue("response_message_field", blankToNull(text(body.get("responseMessageField"))));
        p.addValue("completion_mode", completion);
        p.addValue("active_flag", yesNo(body.get("active"), true));
        String sql =
                "INSERT INTO vending_connector_operations (merchant_id, connector_code, command_type, http_method, "
                        + "command_path, request_template, idempotency_header_name, response_success_field, response_success_value, "
                        + "response_reference_field, response_message_field, completion_mode, active_flag) VALUES "
                        + "(:tenant_merchant_id, :connector_code, :command_type, :http_method, :command_path, :request_template, "
                        + ":idempotency_header_name, :response_success_field, :response_success_value, :response_reference_field, "
                        + ":response_message_field, :completion_mode, :active_flag) ON DUPLICATE KEY UPDATE "
                        + "http_method=VALUES(http_method), command_path=VALUES(command_path), request_template=VALUES(request_template), "
                        + "idempotency_header_name=VALUES(idempotency_header_name), response_success_field=VALUES(response_success_field), "
                        + "response_success_value=VALUES(response_success_value), response_reference_field=VALUES(response_reference_field), "
                        + "response_message_field=VALUES(response_message_field), completion_mode=VALUES(completion_mode), "
                        + "active_flag=VALUES(active_flag)";
        TenantScopeGuard.assertTenantBound(sql);
        jdbc.update(sql, p);
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

    private void validateAuthSecrets(String mode, String authValueCipher, String authSecretCipher) {
        if ("NONE".equals(mode)) return;
        if (("BEARER".equals(mode) || "API_KEY_HEADER".equals(mode))
                && blank(authValueCipher)) {
            throw new PaymentGatewayException("authValue is required for " + mode);
        }
        if ("BASIC".equals(mode) && (blank(authValueCipher) || blank(authSecretCipher))) {
            throw new PaymentGatewayException("authValue and authSecret are required for BASIC");
        }
        if ("HMAC_SHA256_TS_BODY".equals(mode) && blank(authSecretCipher)) {
            throw new PaymentGatewayException("authSecret is required for HMAC authentication");
        }
    }

    private String decryptNullable(Object cipher) {
        String value = text(cipher);
        return value.isBlank() ? "" : crypto.decrypt(value);
    }

    private String decryptRequired(Object cipher, String name) {
        String value = decryptNullable(cipher);
        if (value.isBlank()) {
            throw new PaymentGatewayException("Vending connector " + name + " is missing");
        }
        return value;
    }

    private String allowed(Object value, String fallback, Set<String> allowed, String name) {
        String normalized = normalize(defaulted(value, fallback));
        if (!allowed.contains(normalized)) {
            throw new PaymentGatewayException(name + " is not supported: " + normalized);
        }
        return normalized;
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
        String valueText = text(value);
        return valueText.isBlank() ? null : valueText;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String yesNo(Object value, boolean fallback) {
        if (value == null) return fallback ? "YES" : "NO";
        if (value instanceof Boolean b) return b ? "YES" : "NO";
        String raw = text(value);
        if (raw.isBlank()) return fallback ? "YES" : "NO";
        return ("YES".equalsIgnoreCase(raw)
                        || "TRUE".equalsIgnoreCase(raw)
                        || "1".equals(raw))
                ? "YES"
                : "NO";
    }

    private void validateBaseUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (host == null || host.isBlank() || uri.getUserInfo() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            if ("https".equalsIgnoreCase(scheme)) return;
            if ("http".equalsIgnoreCase(scheme) && isLocalSandbox(url)) return;
        } catch (IllegalArgumentException ignored) {
            // handled below
        }
        throw new PaymentGatewayException(
                "Manufacturer commandBaseUrl must be a valid HTTPS URL (HTTP is allowed only for localhost sandbox testing)");
    }

    private boolean isLocalSandbox(String url) {
        try {
            String host = URI.create(url).getHost();
            return "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host)
                    || "::1".equals(host);
        } catch (Exception ignored) {
            return false;
        }
    }

    private record ExistingSecrets(
            String authValueCiphertext,
            String authSecretCiphertext,
            String callbackSecretCiphertext) {}

    public record Contract(
            long merchantId,
            String connectorCode,
            String commandBaseUrl,
            String authMode,
            String authHeaderName,
            String authTimestampHeader,
            String authKeyHeader,
            String authSignatureEncoding,
            String authSigningTemplate,
            String authValue,
            String authSecret,
            String callbackSecret,
            String callbackSignatureMode,
            String callbackSignatureEncoding,
            String callbackSignatureHeader,
            String callbackTimestampHeader,
            String callbackNonceHeader,
            String callbackEventTypeField,
            String callbackEventIdField,
            String callbackDeviceField,
            String callbackRentalField,
            String callbackAssetField,
            String callbackAvailableCountField) {}

    public record Operation(
            String commandType,
            String httpMethod,
            String commandPath,
            String requestTemplate,
            String idempotencyHeaderName,
            String responseSuccessField,
            String responseSuccessValue,
            String responseReferenceField,
            String responseMessageField,
            String completionMode) {}
}
