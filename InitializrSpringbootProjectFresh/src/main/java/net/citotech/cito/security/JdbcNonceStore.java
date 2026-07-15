package net.citotech.cito.security;

import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "cpay.security.nonce-store", havingValue = "jdbc")
public class JdbcNonceStore implements NonceStore {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcNonceStore(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean remember(String merchantNumber, String nonce, Instant expiresAt) {
        cleanupExpired();
        try {
            String sql = "INSERT INTO cpay_request_nonces "
                    + "(merchant_number, nonce_value, expires_at, created_at) "
                    + "VALUES (:merchant_number, :nonce_value, :expires_at, CURRENT_TIMESTAMP)";
            MapSqlParameterSource parameters = new MapSqlParameterSource();
            parameters.addValue("merchant_number", merchantNumber.trim());
            parameters.addValue("nonce_value", nonce.trim());
            parameters.addValue("expires_at", Timestamp.from(expiresAt));
            jdbcTemplate.update(sql, parameters);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public void cleanupExpired() {
        String sql = "DELETE FROM cpay_request_nonces WHERE expires_at < CURRENT_TIMESTAMP";
        jdbcTemplate.update(sql, new MapSqlParameterSource());
    }
}

