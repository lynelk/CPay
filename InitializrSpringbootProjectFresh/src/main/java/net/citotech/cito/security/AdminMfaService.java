package net.citotech.cito.security;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.merchant.MerchantChannelCryptoService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdminMfaService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TotpService totpService;
    private final MerchantChannelCryptoService cryptoService;

    public AdminMfaService(NamedParameterJdbcTemplate jdbcTemplate,
                           TotpService totpService,
                           MerchantChannelCryptoService cryptoService) {
        this.jdbcTemplate = jdbcTemplate;
        this.totpService = totpService;
        this.cryptoService = cryptoService;
    }

    public Map<String, Object> beginEnrollment(String email) {
        AdminIdentity admin = findAdmin(email);
        String secret = totpService.generateSecret();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("admin_id", admin.id);
        p.addValue("secret", cryptoService.encrypt(secret));
        jdbcTemplate.update(
            "INSERT INTO admin_mfa_totp (admin_id, secret_value, enabled_flag) VALUES (:admin_id, :secret, 'NO') "
                + "ON DUPLICATE KEY UPDATE secret_value=:secret, enabled_flag='NO', verified_at=NULL, updated_at=CURRENT_TIMESTAMP",
            p);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("adminEmail", admin.email);
        response.put("secret", secret);
        response.put("otpauthUrl", totpService.otpauthUrl("CPay Admin", admin.email, secret));
        return response;
    }

    public boolean confirmEnrollment(String email, String code) {
        AdminIdentity admin = findAdmin(email);
        String secret = secretForAdmin(admin.id);
        if (!totpService.verify(secret, code)) {
            return false;
        }
        jdbcTemplate.update(
            "UPDATE admin_mfa_totp SET enabled_flag='YES', verified_at=CURRENT_TIMESTAMP WHERE admin_id=:admin_id",
            new MapSqlParameterSource("admin_id", admin.id));
        return true;
    }

    public boolean isEnabled(long adminId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM admin_mfa_totp WHERE admin_id=:admin_id AND enabled_flag='YES'",
            new MapSqlParameterSource("admin_id", adminId),
            Integer.class);
        return count != null && count > 0;
    }

    public boolean verifyAdminCode(long adminId, String code) {
        return totpService.verify(secretForAdmin(adminId), code);
    }

    private String secretForAdmin(long adminId) {
        List<String> rows = jdbcTemplate.query(
            "SELECT secret_value FROM admin_mfa_totp WHERE admin_id=:admin_id LIMIT 1",
            new MapSqlParameterSource("admin_id", adminId),
            (rs, rowNum) -> rs.getString("secret_value"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("MFA is not enrolled for admin");
        }
        try {
            return cryptoService.decrypt(rows.get(0));
        } catch (IllegalStateException ignored) {
            return rows.get(0);
        }
    }

    private AdminIdentity findAdmin(String email) {
        List<AdminIdentity> rows = jdbcTemplate.query(
            "SELECT id, email FROM admins WHERE email=:email LIMIT 1",
            new MapSqlParameterSource("email", email),
            (rs, rowNum) -> new AdminIdentity(rs.getLong("id"), rs.getString("email")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Admin was not found");
        }
        return rows.get(0);
    }

    private record AdminIdentity(long id, String email) {
    }
}
