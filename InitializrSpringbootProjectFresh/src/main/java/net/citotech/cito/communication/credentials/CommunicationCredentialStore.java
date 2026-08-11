package net.citotech.cito.communication.credentials;

import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantKeyEncryptionService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Encrypted-at-rest store for {@code communication_provider_credentials} (V54, track B6, ISO/IEC
 * 27001 A.8.24). Provider secrets (API keys, passwords, tokens) live here — never in the settings
 * table in plaintext. Values are AES-GCM envelopes written by {@link MerchantKeyEncryptionService}
 * using the {@code CPAY_KEY_ENCRYPTION_KEY} key material (falling back to the channel-encryption
 * key on existing installs), the same key family the V54 migration documents for this table.
 *
 * <p>{@link #credential} decrypts a single value on demand for the adapters; the admin controller
 * only ever sees masked values. A missing row throws — a provider adapter that depends on a
 * credential must fail closed rather than send with an empty secret.
 */
@Repository
public class CommunicationCredentialStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantKeyEncryptionService encryptionService;

    public CommunicationCredentialStore(
            NamedParameterJdbcTemplate jdbcTemplate,
            MerchantKeyEncryptionService encryptionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.encryptionService = encryptionService;
    }

    /** The decrypted value for a provider credential key, or throws when unset. */
    public String credential(String providerCode, String credentialKey) {
        List<String> rows =
                jdbcTemplate.query(
                        "SELECT credential_value_encrypted FROM communication_provider_credentials"
                                + " WHERE provider_code=:provider_code AND credential_key=:credential_key"
                                + " LIMIT 1",
                        new MapSqlParameterSource()
                                .addValue("provider_code", providerCode)
                                .addValue("credential_key", credentialKey),
                        (rs, rowNum) -> rs.getString("credential_value_encrypted"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException(
                    "Provider credential "
                            + providerCode
                            + "/"
                            + credentialKey
                            + " is not configured");
        }
        return encryptionService.decrypt(rows.get(0));
    }

    /** Saves (upserts) one encrypted credential. Returns the masked view. */
    public CredentialRow save(String providerCode, String credentialKey, String plainValue) {
        if (providerCode == null || providerCode.isBlank()) {
            throw new PaymentGatewayException("providerCode is required");
        }
        if (credentialKey == null || credentialKey.isBlank()) {
            throw new PaymentGatewayException("credentialKey is required");
        }
        if (plainValue == null || plainValue.isBlank()) {
            throw new PaymentGatewayException("credential value must not be blank");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", providerCode.trim());
        p.addValue("credential_key", credentialKey.trim());
        p.addValue("credential_value_encrypted", encryptionService.encrypt(plainValue));
        jdbcTemplate.update(
                "INSERT INTO communication_provider_credentials (provider_code, credential_key,"
                        + " credential_value_encrypted) VALUES (:provider_code, :credential_key,"
                        + " :credential_value_encrypted) ON DUPLICATE KEY UPDATE"
                        + " credential_value_encrypted=VALUES(credential_value_encrypted)",
                p);
        return new CredentialRow(providerCode.trim(), credentialKey.trim(), mask(plainValue));
    }

    /** All stored credential keys for a provider with masked values (admin view). */
    public List<CredentialRow> listForProvider(String providerCode) {
        return jdbcTemplate.query(
                "SELECT provider_code, credential_key, credential_value_encrypted"
                        + " FROM communication_provider_credentials WHERE provider_code=:provider_code"
                        + " ORDER BY credential_key ASC",
                new MapSqlParameterSource("provider_code", providerCode),
                (rs, rowNum) ->
                        new CredentialRow(
                                rs.getString("provider_code"),
                                rs.getString("credential_key"),
                                mask(rs.getString("credential_value_encrypted"))));
    }

    public int delete(String providerCode, String credentialKey) {
        return jdbcTemplate.update(
                "DELETE FROM communication_provider_credentials"
                        + " WHERE provider_code=:provider_code AND credential_key=:credential_key",
                new MapSqlParameterSource()
                        .addValue("provider_code", providerCode)
                        .addValue("credential_key", credentialKey));
    }

    private String mask(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 4
                ? "****"
                : value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    public record CredentialRow(String providerCode, String credentialKey, String maskedValue) {}
}
