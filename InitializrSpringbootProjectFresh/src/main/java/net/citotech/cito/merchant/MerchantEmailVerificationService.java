package net.citotech.cito.merchant;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.SendMail;
import net.citotech.cito.async.ManagedAsyncTasks;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirms a merchant portal user (merchant_admins) actually owns the email address they signed up
 * with, before letting them log in (audit P4). Self-service signup lets anyone create an account
 * with any email address; nothing previously checked that the signer-upper could receive mail at
 * it. Deliberately separate from the existing email_verification_code column, which is already the
 * live mechanism for password-reset OTPs - reusing it here would let a password-reset request
 * consume/overwrite an in-flight signup verification code, or vice versa.
 */
@Service
public class MerchantEmailVerificationService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Value("${cpay.email-verification.expiry-hours:24}")
    private long expiryHours;

    public MerchantEmailVerificationService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Issues a fresh code and emails it, invalidating any earlier unconsumed code for this user. */
    @Transactional
    public void sendVerificationEmail(long merchantAdminId, String email, String name) {
        String code = Common.randomNumericString(6);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_admin_id", merchantAdminId);
        p.addValue("token_hash", CanonicalRequestSigner.sha256Hex(code));
        p.addValue("expires_at", Timestamp.from(Instant.now().plus(expiryHours, ChronoUnit.HOURS)));
        jdbcTemplate.update(
            "UPDATE merchant_email_verification_tokens SET consumed_at=CURRENT_TIMESTAMP "
                + "WHERE merchant_admin_id=:merchant_admin_id AND consumed_at IS NULL",
            p);
        jdbcTemplate.update(
            "INSERT INTO merchant_email_verification_tokens (merchant_admin_id, token_hash, expires_at) "
                + "VALUES (:merchant_admin_id, :token_hash, :expires_at)",
            p);

        Setting template = Common.getSettings("email_tmp_merchant_email_verification", jdbcTemplate);
        String body = (template == null ? "Hi {name}, your verification code is {verification_code}." : template.getSetting_value())
            .replace("{name}", name == null ? "" : name)
            .replace("{verification_code}", code);

        ManagedAsyncTasks.run("merchant-email-verification-" + merchantAdminId, () -> {
            SendMail mail = new SendMail();
            mail.sendSimpleMessage(email, "Confirm your email address", body, jdbcTemplate);
        });
    }

    /** Marks the merchant user verified if {@code code} is a live, unconsumed, unexpired token for them. */
    @Transactional
    public boolean verify(long merchantAdminId, String code) {
        if (isBlank(code)) {
            return false;
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_admin_id", merchantAdminId);
        p.addValue("token_hash", CanonicalRequestSigner.sha256Hex(code.trim()));
        List<Long> rows = jdbcTemplate.query(
            "SELECT id FROM merchant_email_verification_tokens "
                + "WHERE merchant_admin_id=:merchant_admin_id AND token_hash=:token_hash "
                + "AND consumed_at IS NULL AND expires_at > CURRENT_TIMESTAMP "
                + "ORDER BY id DESC LIMIT 1",
            p,
            (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            return false;
        }
        p.addValue("id", rows.get(0));
        jdbcTemplate.update(
            "UPDATE merchant_email_verification_tokens SET consumed_at=CURRENT_TIMESTAMP WHERE id=:id",
            p);
        int updated = jdbcTemplate.update(
            "UPDATE merchant_admins SET email_verified_at=CURRENT_TIMESTAMP "
                + "WHERE id=:merchant_admin_id AND email_verified_at IS NULL",
            p);
        if (updated == 0) {
            // Already verified by an earlier request; still a legitimate outcome, not an error.
            return isAlreadyVerified(merchantAdminId);
        }
        return true;
    }

    /** Resend entry point for a not-yet-logged-in user (no session exists pre-verification). */
    public void resendVerificationEmail(String merchantNumber, String email) {
        MerchantAdminRef ref = requireMerchantAdmin(merchantNumber, email);
        sendVerificationEmail(ref.id(), ref.email(), ref.name());
    }

    /** Verify entry point for a not-yet-logged-in user (no session exists pre-verification). */
    public boolean verifyByMerchantNumberAndEmail(String merchantNumber, String email, String code) {
        MerchantAdminRef ref = requireMerchantAdmin(merchantNumber, email);
        return verify(ref.id(), code);
    }

    private MerchantAdminRef requireMerchantAdmin(String merchantNumber, String email) {
        if (isBlank(merchantNumber) || isBlank(email)) {
            throw new PaymentGatewayException("merchantNumber and email are required");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("account_number", merchantNumber.trim());
        p.addValue("email", email.trim().toLowerCase());
        List<MerchantAdminRef> rows = jdbcTemplate.query(
            "SELECT a.id, a.email, a.name FROM merchant_admins a "
                + "JOIN merchants m ON m.id = a.merchant_id "
                + "WHERE m.account_number=:account_number AND a.email=:email",
            p,
            (rs, rowNum) -> new MerchantAdminRef(rs.getLong("id"), rs.getString("email"), rs.getString("name")));
        return rows.stream().findFirst()
            .orElseThrow(() -> new PaymentGatewayException("Merchant user was not found"));
    }

    private record MerchantAdminRef(long id, String email, String name) {
    }

    private boolean isAlreadyVerified(long merchantAdminId) {
        MapSqlParameterSource p = new MapSqlParameterSource("merchant_admin_id", merchantAdminId);
        List<String> rows = jdbcTemplate.query(
            "SELECT email_verified_at FROM merchant_admins WHERE id=:merchant_admin_id",
            p,
            (rs, rowNum) -> rs.getString("email_verified_at"));
        return !rows.isEmpty() && rows.get(0) != null;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
