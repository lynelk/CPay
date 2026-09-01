package net.citotech.cito.security;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Common;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues a short-lived, single-use password-reset token for an existing active administrator.
 *
 * <p>This service accepts only the SHA-256 digest of the operator-held reset code. The raw code is
 * never stored in application configuration, the database, or logs.
 */
@Service
public class AdminRecoveryTokenIssuer {

    private static final String ENTITY_TYPE = "ADMIN";
    private static final String REQUEST_SOURCE = "ops-admin-recovery";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final long expiryMinutes;

    public AdminRecoveryTokenIssuer(
            NamedParameterJdbcTemplate jdbcTemplate,
            @Value("${cpay.password-reset.expiry-minutes:15}") long expiryMinutes) {
        this.jdbcTemplate = jdbcTemplate;
        this.expiryMinutes = Math.max(1L, expiryMinutes);
    }

    @Transactional
    public IssueResult issue(String rawEmail, String rawTokenSha256) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        String tokenSha256 = rawTokenSha256.trim().toLowerCase(Locale.ROOT);

        MapSqlParameterSource accountParameters =
                new MapSqlParameterSource().addValue("email", email);
        List<Map<String, Object>> accounts =
                jdbcTemplate.queryForList(
                        "SELECT id, email, status FROM "
                                + Common.DB_TABLE_ADMIN
                                + " WHERE LOWER(email)=:email ORDER BY id LIMIT 2",
                        accountParameters);
        if (accounts.isEmpty()) {
            return IssueResult.ACCOUNT_NOT_FOUND;
        }
        if (accounts.size() != 1) {
            return IssueResult.AMBIGUOUS_ACCOUNT;
        }

        Map<String, Object> account = accounts.get(0);
        String status = String.valueOf(account.get("status"));
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return IssueResult.ACCOUNT_NOT_ACTIVE;
        }

        long accountId = ((Number) account.get("id")).longValue();
        String canonicalEmail = String.valueOf(account.get("email")).trim().toLowerCase(Locale.ROOT);
        Instant issuedAt = Instant.now();
        Timestamp expiresAt =
                Timestamp.from(issuedAt.plus(expiryMinutes, ChronoUnit.MINUTES));
        Timestamp legacyWindowStart =
                Timestamp.from(
                        issuedAt.plus(Math.max(0L, expiryMinutes - 5L), ChronoUnit.MINUTES));
        MapSqlParameterSource tokenParameters =
                new MapSqlParameterSource()
                        .addValue("entity_type", ENTITY_TYPE)
                        .addValue("entity_id", accountId)
                        .addValue("email", canonicalEmail)
                        .addValue("token_hash", tokenSha256)
                        .addValue("request_ip", REQUEST_SOURCE)
                        .addValue("expires_at", expiresAt);

        try {
            int inserted =
                    jdbcTemplate.update(
                            "INSERT INTO password_reset_tokens "
                                    + "(entity_type, entity_id, email, token_hash, request_ip, expires_at) "
                                    + "VALUES (:entity_type, :entity_id, :email, :token_hash, :request_ip, :expires_at)",
                            tokenParameters);
            if (inserted != 1) {
                return IssueResult.ALREADY_PROCESSED;
            }

            MapSqlParameterSource accountUpdateParameters =
                    new MapSqlParameterSource()
                            .addValue("id", accountId)
                            .addValue("email_verification_sent_on", legacyWindowStart);
            int updated =
                    jdbcTemplate.update(
                            "UPDATE "
                                    + Common.DB_TABLE_ADMIN
                                    + " SET email_verification_sent_on=:email_verification_sent_on "
                                    + "WHERE id=:id",
                            accountUpdateParameters);
            if (updated != 1) {
                throw new IllegalStateException(
                        "Admin recovery token issued but account reset window was not updated");
            }
            return IssueResult.ISSUED;
        } catch (DuplicateKeyException ignored) {
            return IssueResult.ALREADY_PROCESSED;
        }
    }

    public enum IssueResult {
        ISSUED,
        ALREADY_PROCESSED,
        ACCOUNT_NOT_FOUND,
        ACCOUNT_NOT_ACTIVE,
        AMBIGUOUS_ACCOUNT
    }
}
