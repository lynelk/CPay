package net.citotech.cito.merchant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Model.MerchantUser;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.PasswordUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Role-only merchant team management. No merchant privilege table is read or written here. */
@Service
public class MerchantTeamService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final MerchantEmailVerificationService emailVerificationService;

    public MerchantTeamService(
            NamedParameterJdbcTemplate jdbcTemplate,
            MerchantEmailVerificationService emailVerificationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.emailVerificationService = emailVerificationService;
    }

    public List<Map<String, Object>> list(long merchantId) {
        return jdbcTemplate.queryForList(
                "SELECT id,name,email,phone,status,role,email_verified_at,created_on,updated_on "
                        + "FROM merchant_admins WHERE merchant_id=:merchant_id ORDER BY id ASC",
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    @Transactional
    public long create(long merchantId, Map<String, Object> body) {
        String name = required(body, "name");
        String email = required(body, "email").toLowerCase(Locale.ROOT);
        String phone = required(body, "phone");
        String password = required(body, "password");
        String status = status(body.get("status"));
        MerchantRole role = assignedRole(body.get("role"));
        ensureUniqueEmail(merchantId, email, null);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("merchant_id", merchantId);
        params.addValue("name", name);
        params.addValue("email", email);
        params.addValue("phone", phone);
        params.addValue("password", PasswordUtils.hashPassword(password));
        params.addValue("status", status);
        params.addValue("role", role.name());
        KeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(
                "INSERT INTO merchant_admins "
                        + "(merchant_id,name,email,phone,password,status,role) VALUES "
                        + "(:merchant_id,:name,:email,:phone,:password,:status,:role)",
                params,
                key);
        Number id = key.getKey();
        if (id == null) throw new PaymentGatewayException("Unable to create merchant user");
        emailVerificationService.sendVerificationEmail(id.longValue(), email, name);
        return id.longValue();
    }

    @Transactional
    public void update(
            long merchantId, long actingUserId, long userId, Map<String, Object> body) {
        Map<String, Object> current = requireMember(merchantId, userId);
        String name = required(body, "name");
        String email = required(body, "email").toLowerCase(Locale.ROOT);
        String phone = required(body, "phone");
        String status = status(body.get("status"));
        MerchantRole nextRole = assignedRole(body.get("role"));
        MerchantRole currentRole = MerchantRole.fromString(String.valueOf(current.get("role")));

        if (actingUserId == userId && nextRole != MerchantRole.OWNER) {
            throw new PaymentGatewayException("An OWNER cannot demote their own active session");
        }
        if (currentRole == MerchantRole.OWNER && nextRole != MerchantRole.OWNER) {
            ensureAnotherOwner(merchantId, userId);
        }
        ensureUniqueEmail(merchantId, email, userId);

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("merchant_id", merchantId);
        params.addValue("id", userId);
        params.addValue("name", name);
        params.addValue("email", email);
        params.addValue("phone", phone);
        params.addValue("status", status);
        params.addValue("role", nextRole.name());
        String password = text(body.get("password"));
        if (password.isEmpty()) {
            jdbcTemplate.update(
                    "UPDATE merchant_admins SET name=:name,email=:email,phone=:phone,status=:status,role=:role "
                            + "WHERE id=:id AND merchant_id=:merchant_id",
                    params);
        } else {
            params.addValue("password", PasswordUtils.hashPassword(password));
            jdbcTemplate.update(
                    "UPDATE merchant_admins SET name=:name,email=:email,phone=:phone,status=:status,role=:role,password=:password "
                            + "WHERE id=:id AND merchant_id=:merchant_id",
                    params);
        }
    }

    @Transactional
    public void delete(long merchantId, long actingUserId, long userId) {
        if (actingUserId == userId) {
            throw new PaymentGatewayException("You cannot delete the user attached to your current session");
        }
        Map<String, Object> current = requireMember(merchantId, userId);
        MerchantRole role = MerchantRole.fromString(String.valueOf(current.get("role")));
        if (role == MerchantRole.OWNER) ensureAnotherOwner(merchantId, userId);
        jdbcTemplate.update(
                "DELETE FROM merchant_admins WHERE id=:id AND merchant_id=:merchant_id",
                new MapSqlParameterSource("id", userId).addValue("merchant_id", merchantId));
    }

    private Map<String, Object> requireMember(long merchantId, long userId) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id,email,role FROM merchant_admins WHERE id=:id AND merchant_id=:merchant_id",
                        new MapSqlParameterSource("id", userId).addValue("merchant_id", merchantId));
        if (rows.isEmpty()) throw new PaymentGatewayException("Merchant user does not exist");
        return rows.get(0);
    }

    private void ensureAnotherOwner(long merchantId, long excludedUserId) {
        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM merchant_admins WHERE merchant_id=:merchant_id "
                                + "AND role='OWNER' AND id<>:id AND status='ACTIVE'",
                        new MapSqlParameterSource("merchant_id", merchantId).addValue("id", excludedUserId),
                        Integer.class);
        if (count == null || count < 1) {
            throw new PaymentGatewayException("The merchant account must retain at least one active OWNER");
        }
    }

    private void ensureUniqueEmail(long merchantId, String email, Long excludedId) {
        MapSqlParameterSource params =
                new MapSqlParameterSource("merchant_id", merchantId).addValue("email", email);
        String sql = "SELECT COUNT(*) FROM merchant_admins WHERE merchant_id=:merchant_id AND email=:email";
        if (excludedId != null) {
            sql += " AND id<>:id";
            params.addValue("id", excludedId);
        }
        Integer count = jdbcTemplate.queryForObject(sql, params, Integer.class);
        if (count != null && count > 0) throw new PaymentGatewayException("This email is already registered");
    }

    private MerchantRole assignedRole(Object value) {
        String raw = text(value);
        if (raw.isEmpty()) return MerchantRole.VIEWER;
        try {
            return MerchantRole.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PaymentGatewayException("role must be OWNER, FINANCE, DEVELOPER or VIEWER");
        }
    }

    private String status(Object value) {
        String raw = text(value).toUpperCase(Locale.ROOT);
        if (raw.isEmpty()) return "ACTIVE";
        if (!raw.equals("ACTIVE") && !raw.equals("INACTIVE") && !raw.equals("SUSPENDED")) {
            throw new PaymentGatewayException("status must be ACTIVE, INACTIVE or SUSPENDED");
        }
        return raw;
    }

    private String required(Map<String, Object> body, String key) {
        String value = text(body == null ? null : body.get(key));
        if (value.isEmpty()) throw new PaymentGatewayException(key + " is required");
        return value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
