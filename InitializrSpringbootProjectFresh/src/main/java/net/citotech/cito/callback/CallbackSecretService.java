package net.citotech.cito.callback;

import java.security.SecureRandom;
import java.util.Base64;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Audit E13: {@code merchant_callback_secrets.secret_value} was stored in plaintext, unlike the
 * equivalent {@code merchant_webhook_endpoints} secret (encrypted + separately hashed via {@link
 * net.citotech.cito.webhook.MerchantWebhookService}). New secrets are now encrypted at rest with
 * {@link MerchantChannelCryptoService} on write and decrypted on read; existing plaintext rows
 * (written before this change) are tolerated on read via a decrypt-with-fallback, the same pattern
 * {@code MerchantKeyCryptoRegistry} already uses for merchant RSA keys - they are not rewritten
 * until the merchant's secret is next rotated.
 */
@Service
public class CallbackSecretService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantChannelCryptoService cryptoService;
    private final String fallbackSecret;
    private final SecureRandom random = new SecureRandom();

    public CallbackSecretService(
            NamedParameterJdbcTemplate jdbcTemplate,
            MerchantChannelCryptoService cryptoService,
            @Value("${callback.signing.secret}") String fallbackSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.cryptoService = cryptoService;
        this.fallbackSecret = fallbackSecret;
    }

    public String activeSecret(long merchantId) {
        String sql =
                "SELECT secret_value FROM merchant_callback_secrets WHERE merchant_id=:merchant_id AND active_flag='YES' ORDER BY id DESC LIMIT 1";
        MapSqlParameterSource p = new MapSqlParameterSource("merchant_id", merchantId);
        try {
            String value = jdbcTemplate.queryForObject(sql, p, String.class);
            return value == null || value.trim().isEmpty() ? fallbackSecret : decrypt(value);
        } catch (Exception e) {
            return fallbackSecret;
        }
    }

    public String rotate(long merchantId, String alias) {
        String secret = generate();
        jdbcTemplate.update(
                "UPDATE merchant_callback_secrets SET active_flag='NO', rotated_at=CURRENT_TIMESTAMP WHERE merchant_id=:merchant_id",
                new MapSqlParameterSource("merchant_id", merchantId));
        String sql =
                "INSERT INTO merchant_callback_secrets (merchant_id, secret_alias, secret_value) VALUES (:merchant_id, :alias, :secret)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("alias", alias == null || alias.trim().isEmpty() ? "default" : alias);
        p.addValue("secret", cryptoService.encrypt(secret));
        jdbcTemplate.update(sql, p);
        return secret;
    }

    /**
     * Count of merchants with no active row in {@code merchant_callback_secrets} at all - these
     * fall back to the single shared {@code callback.signing.secret} on every callback (audit E12).
     * There was previously no way to see this without querying the table directly.
     */
    public int countMerchantsOnFallbackSecret() {
        String sql =
                "SELECT COUNT(*) FROM "
                        + net.citotech.cito.Common.DB_TABLE_MERCHANTS
                        + " m WHERE NOT EXISTS (SELECT 1 FROM merchant_callback_secrets s "
                        + "WHERE s.merchant_id = m.id AND s.active_flag='YES')";
        Integer count =
                jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(), Integer.class);
        return count == null ? 0 : count;
    }

    /** Count of active merchant secrets currently stored as legacy plaintext (audit E13/E12). */
    public int countLegacyPlaintextSecrets() {
        String sql = "SELECT secret_value FROM merchant_callback_secrets WHERE active_flag='YES'";
        java.util.List<String> values =
                jdbcTemplate.queryForList(sql, new MapSqlParameterSource(), String.class);
        int found = 0;
        for (String value : values) {
            if (isLegacyPlaintext(value)) {
                found++;
            }
        }
        return found;
    }

    private String decrypt(String stored) {
        if (isLegacyPlaintext(stored)) {
            return stored;
        }
        return cryptoService.decrypt(stored);
    }

    private boolean isLegacyPlaintext(String stored) {
        try {
            cryptoService.decrypt(stored);
            return false;
        } catch (Exception ex) {
            return true;
        }
    }

    private String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
