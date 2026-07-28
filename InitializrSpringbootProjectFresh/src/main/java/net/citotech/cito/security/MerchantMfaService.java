package net.citotech.cito.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MerchantMfaService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TotpService totpService;
    private final MerchantChannelCryptoService cryptoService;

    public MerchantMfaService(NamedParameterJdbcTemplate jdbcTemplate,
                              TotpService totpService,
                              MerchantChannelCryptoService cryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.totpService = totpService;
        this.cryptoService = cryptoService;
    }

    public Map<String, Object> beginEnrollment(String accountNumber, String email) {
        MerchantAdminIdentity admin = findMerchantAdmin(accountNumber, email);
        String secret = totpService.generateSecret();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("admin_id", admin.id());
        p.addValue("secret", cryptoService.encrypt(secret));
        jdbcTemplate.update(
            "INSERT INTO merchant_mfa_totp (merchant_admin_id, secret_value, enabled_flag) "
                + "VALUES (:admin_id, :secret, 'NO') "
                + "ON DUPLICATE KEY UPDATE secret_value=:secret, enabled_flag='NO', verified_at=NULL, updated_at=CURRENT_TIMESTAMP",
            p);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("merchantAccount", admin.accountNumber());
        response.put("adminEmail", admin.email());
        response.put("secret", secret);
        response.put("otpauthUrl", totpService.otpauthUrl("CPay Merchant", admin.accountNumber() + ":" + admin.email(), secret));
        return response;
    }

    public boolean confirmEnrollment(String accountNumber, String email, String code) {
        MerchantAdminIdentity admin = findMerchantAdmin(accountNumber, email);
        String secret = secretForAdmin(admin.id());
        if (!totpService.verify(secret, code)) {
            return false;
        }
        jdbcTemplate.update(
            "UPDATE merchant_mfa_totp SET enabled_flag='YES', verified_at=CURRENT_TIMESTAMP WHERE merchant_admin_id=:admin_id",
            new MapSqlParameterSource("admin_id", admin.id()));
        return true;
    }

    public boolean isEnabled(long merchantAdminId) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM merchant_mfa_totp WHERE merchant_admin_id=:admin_id AND enabled_flag='YES'",
                new MapSqlParameterSource("admin_id", merchantAdminId),
                Integer.class);
            return count != null && count > 0;
        } catch (BadSqlGrammarException ex) {
            // Fail closed: an unreadable MFA store must never be treated as "MFA disabled",
            // or a broken/missing table would silently bypass the second factor at login.
            Logger.getLogger(MerchantMfaService.class.getName()).log(Level.SEVERE,
                "Merchant MFA store is unreadable (merchant_admin_id=" + merchantAdminId + "); denying login "
                    + "instead of bypassing MFA", ex);
            throw new PaymentGatewayException("MFA verification is temporarily unavailable", ex);
        }
    }

    public boolean verifyCode(long merchantAdminId, String code) {
        return totpService.verify(secretForAdmin(merchantAdminId), code);
    }

    private String secretForAdmin(long adminId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT secret_value FROM merchant_mfa_totp WHERE merchant_admin_id=:admin_id LIMIT 1",
            new MapSqlParameterSource("admin_id", adminId),
            (rs, rowNum) -> rs.getString("secret_value"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("MFA is not enrolled for merchant admin");
        }
        try {
            return cryptoService.decrypt(rows.get(0));
        } catch (IllegalStateException ignored) {
            return rows.get(0);
        }
    }

    private MerchantAdminIdentity findMerchantAdmin(String accountNumber, String email) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("account_number", required(accountNumber, "accountNumber"));
        p.addValue("email", required(email, "email").toLowerCase());
        List<MerchantAdminIdentity> rows = jdbcTemplate.query(
            "SELECT a.id, a.email, m.account_number "
                + "FROM merchant_admins a JOIN merchants m ON m.id=a.merchant_id "
                + "WHERE m.account_number=:account_number AND LOWER(a.email)=:email LIMIT 1",
            p,
            (rs, rowNum) -> new MerchantAdminIdentity(
                rs.getLong("id"),
                rs.getString("email"),
                rs.getString("account_number")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Merchant admin was not found");
        }
        return rows.get(0);
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private record MerchantAdminIdentity(long id, String email, String accountNumber) {
    }
}
