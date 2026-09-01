package net.citotech.cito.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(
        path = "/auth",
        produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
public class InitialPasswordChangeController {

    static final String PENDING_ADMIN_ID_SESSION_ATTRIBUTE = "pendingAdminPasswordChangeId";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final SessionRevocationService sessionRevocationService;

    public InitialPasswordChangeController(
            NamedParameterJdbcTemplate jdbcTemplate,
            SessionRevocationService sessionRevocationService) {
        this.jdbcTemplate = jdbcTemplate;
        this.sessionRevocationService = sessionRevocationService;
    }

    @PostMapping(path = "/completeInitialPasswordChange")
    @Transactional
    public String completeInitialPasswordChange(
            @RequestBody Map<String, String> requestBody, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long adminId = pendingAdminId(session);
        if (adminId == null) {
            return response(
                    "PASSWORD_CHANGE_SESSION_REQUIRED",
                    "Sign in with the temporary password before choosing a new password.");
        }

        String newPassword = value(requestBody, "new_password");
        String confirmation = value(requestBody, "confirm_password");
        if (!newPassword.equals(confirmation)) {
            return response(
                    "PASSWORD_CONFIRMATION_MISMATCH",
                    "The new password does not match the confirmation.");
        }
        String policyError = passwordPolicyError(newPassword);
        if (policyError != null) {
            return response("PASSWORD_POLICY_FAILED", policyError);
        }

        List<Map<String, Object>> accounts =
                jdbcTemplate.queryForList(
                        "SELECT id, status, password, must_change_password FROM admins "
                                + "WHERE id=:id FOR UPDATE",
                        new MapSqlParameterSource("id", adminId));
        if (accounts.size() != 1
                || !"ACTIVE".equalsIgnoreCase(String.valueOf(accounts.get(0).get("status")))
                || !isPasswordChangeRequired(accounts.get(0).get("must_change_password"))) {
            session.invalidate();
            return response(
                    "PASSWORD_CHANGE_SESSION_REQUIRED",
                    "The password-change session is no longer valid. Sign in again.");
        }
        if (PasswordUtils.verifyPassword(
                newPassword, String.valueOf(accounts.get(0).get("password")))) {
            return response(
                    "PASSWORD_REUSE_NOT_ALLOWED",
                    "Choose a password different from the temporary password.");
        }

        MapSqlParameterSource updateParameters =
                new MapSqlParameterSource()
                        .addValue("id", adminId)
                        .addValue("password", PasswordUtils.hashPassword(newPassword));
        int updated =
                jdbcTemplate.update(
                        "UPDATE admins SET password=:password, must_change_password=0, "
                                + "email_verification_code='', "
                                + "email_verification_sent_on=CURRENT_TIMESTAMP "
                                + "WHERE id=:id AND must_change_password=1",
                        updateParameters);
        if (updated != 1) {
            throw new IllegalStateException("Initial administrator password was not updated");
        }

        sessionRevocationService.revokeAllForAdmin(adminId);
        session.invalidate();
        return response("000", "Password changed. Sign in again with your new password.");
    }

    private Long pendingAdminId(HttpSession session) {
        if (session == null) {
            return null;
        }
        Object value = session.getAttribute(PENDING_ADMIN_ID_SESSION_ATTRIBUTE);
        return value instanceof Number number ? number.longValue() : null;
    }

    private String value(Map<String, String> requestBody, String key) {
        if (requestBody == null || requestBody.get(key) == null) {
            return "";
        }
        return requestBody.get(key);
    }

    private boolean isPasswordChangeRequired(Object value) {
        if (value instanceof Boolean required) {
            return required;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return "1".equals(String.valueOf(value));
    }

    private String passwordPolicyError(String password) {
        int bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (password.length() < 12 || bytes > 72) {
            return "Use 12 to 72 characters.";
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            return "Do not use spaces in the password.";
        }
        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSymbol = password.chars().anyMatch(value -> !Character.isLetterOrDigit(value));
        if (!hasUppercase || !hasLowercase || !hasDigit || !hasSymbol) {
            return "Include an uppercase letter, lowercase letter, number, and symbol.";
        }
        return null;
    }

    private String response(String code, String message) {
        return new JSONObject().put("code", code).put("message", message).toString();
    }
}
