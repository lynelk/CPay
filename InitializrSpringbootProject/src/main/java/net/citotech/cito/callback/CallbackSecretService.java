package net.citotech.cito.callback;

import java.util.Base64;
import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CallbackSecretService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final String fallbackSecret;
    private final SecureRandom random = new SecureRandom();

    public CallbackSecretService(NamedParameterJdbcTemplate jdbcTemplate,
                                 @Value("${callback.signing.secret:${actuator.password}}") String fallbackSecret) {
        this.jdbcTemplate = jdbcTemplate;
        this.fallbackSecret = fallbackSecret;
    }

    public String activeSecret(long merchantId) {
        String sql = "SELECT secret_value FROM merchant_callback_secrets WHERE merchant_id=:merchant_id AND active_flag='YES' ORDER BY id DESC LIMIT 1";
        MapSqlParameterSource p = new MapSqlParameterSource("merchant_id", merchantId);
        try {
            String value = jdbcTemplate.queryForObject(sql, p, String.class);
            return value == null || value.trim().isEmpty() ? fallbackSecret : value;
        } catch (Exception e) {
            return fallbackSecret;
        }
    }

    public String rotate(long merchantId, String alias) {
        String secret = generate();
        jdbcTemplate.update("UPDATE merchant_callback_secrets SET active_flag='NO', rotated_at=CURRENT_TIMESTAMP WHERE merchant_id=:merchant_id", new MapSqlParameterSource("merchant_id", merchantId));
        String sql = "INSERT INTO merchant_callback_secrets (merchant_id, secret_alias, secret_value) VALUES (:merchant_id, :alias, :secret)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("alias", alias == null || alias.trim().isEmpty() ? "default" : alias);
        p.addValue("secret", secret);
        jdbcTemplate.update(sql, p);
        return secret;
    }

    private String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
