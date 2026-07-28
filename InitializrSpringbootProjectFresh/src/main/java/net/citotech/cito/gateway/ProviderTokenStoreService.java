package net.citotech.cito.gateway;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderTokenStoreService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantChannelCryptoService cryptoService;

    public ProviderTokenStoreService(NamedParameterJdbcTemplate jdbcTemplate,
                                     MerchantChannelCryptoService cryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.cryptoService = cryptoService;
    }

    @Transactional
    public void save(String providerCode, String segment, String environment, String tokenValue, Instant expiresAt) {
        require(providerCode, "providerCode");
        require(segment, "segment");
        require(environment, "environment");
        require(tokenValue, "tokenValue");

        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", normalize(providerCode));
        p.addValue("segment", normalize(segment));
        p.addValue("environment", normalize(environment));
        p.addValue("token_value", cryptoService.encrypt(tokenValue));
        p.addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt));
        jdbcTemplate.update(
            "INSERT INTO provider_tokens "
                + "(provider_code, segment, environment, token_value, expires_at) "
                + "VALUES (:provider_code, :segment, :environment, :token_value, :expires_at) "
                + "ON DUPLICATE KEY UPDATE token_value=:token_value, expires_at=:expires_at, "
                + "lease_owner=NULL, lease_expires_at=NULL",
            p);
    }

    public Optional<ProviderToken> findValid(String providerCode, String segment, String environment) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", normalize(providerCode));
        p.addValue("segment", normalize(segment));
        p.addValue("environment", normalize(environment));
        List<ProviderToken> tokens = jdbcTemplate.query(
            "SELECT provider_code, segment, environment, token_value, expires_at FROM provider_tokens "
                + "WHERE provider_code=:provider_code AND segment=:segment AND environment=:environment "
                + "AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP) LIMIT 1",
            p,
            (rs, rowNum) -> {
                ProviderToken token = new ProviderToken();
                token.setProviderCode(rs.getString("provider_code"));
                token.setSegment(rs.getString("segment"));
                token.setEnvironment(rs.getString("environment"));
                token.setTokenValue(cryptoService.decrypt(rs.getString("token_value")));
                Timestamp expires = rs.getTimestamp("expires_at");
                token.setExpiresAt(expires == null ? null : expires.toInstant());
                return token;
            });
        return tokens.isEmpty() ? Optional.empty() : Optional.of(tokens.get(0));
    }

    @Transactional
    public boolean acquireRefreshLease(String providerCode, String segment, String environment, String owner, Instant leaseUntil) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("provider_code", normalize(providerCode));
        p.addValue("segment", normalize(segment));
        p.addValue("environment", normalize(environment));
        p.addValue("owner", owner);
        p.addValue("lease_until", Timestamp.from(leaseUntil));
        int updated = jdbcTemplate.update(
            "UPDATE provider_tokens SET lease_owner=:owner, lease_expires_at=:lease_until "
                + "WHERE provider_code=:provider_code AND segment=:segment AND environment=:environment "
                + "AND (lease_expires_at IS NULL OR lease_expires_at < CURRENT_TIMESTAMP OR lease_owner=:owner)",
            p);
        return updated > 0;
    }

    @Transactional
    public int deleteExpired() {
        return jdbcTemplate.update(
            "DELETE FROM provider_tokens WHERE expires_at IS NOT NULL AND expires_at < CURRENT_TIMESTAMP",
            new MapSqlParameterSource());
    }

    private void require(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
