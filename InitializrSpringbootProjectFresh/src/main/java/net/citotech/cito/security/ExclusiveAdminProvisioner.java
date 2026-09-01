package net.citotech.cito.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExclusiveAdminProvisioner {

    private static final List<String> REQUIRED_PRIVILEGES =
            List.of(
                    "ACCESS_ADMIN",
                    "CREATE_ADMIN",
                    "UPDATE_ADMIN",
                    "DELETE_ADMIN",
                    "ACCESS_AUDITTRAIL",
                    "ACCESS_TRANSACTION_LOG",
                    "ACCESS_SMS_LOG",
                    "CREATE_MERCHANT",
                    "UPDATE_MERCHANT",
                    "DELETE_MERCHANT",
                    "CREDIT_MERCHANT",
                    "SEND_SMS",
                    "CREATE_BATCH_TX",
                    "RESOLVE_TRANSACTIONS");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ExclusiveAdminProvisioner(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ProvisionResult apply(
            String operationId, String rawEmail, String name, String bcryptPasswordHash) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        MapSqlParameterSource operationParameters =
                new MapSqlParameterSource()
                        .addValue("operation_id", operationId)
                        .addValue("target_email_sha256", sha256Hex(email));
        int claimed =
                jdbcTemplate.update(
                        "INSERT IGNORE INTO admin_bootstrap_operations "
                                + "(operation_id, target_email_sha256) "
                                + "VALUES (:operation_id, :target_email_sha256)",
                        operationParameters);
        if (claimed == 0) {
            return ProvisionResult.alreadyProcessed();
        }

        List<Map<String, Object>> existingAdmins =
                jdbcTemplate.queryForList(
                        "SELECT id, email FROM admins ORDER BY id FOR UPDATE",
                        new MapSqlParameterSource());
        List<String> inheritedPrivileges =
                jdbcTemplate.queryForList(
                        "SELECT DISTINCT privilege FROM admin_privileges "
                                + "WHERE privilege IS NOT NULL AND privilege <> ''",
                        new MapSqlParameterSource(),
                        String.class);

        List<Long> matchingTargetIds =
                existingAdmins.stream()
                        .filter(row -> email.equalsIgnoreCase(String.valueOf(row.get("email")).trim()))
                        .map(row -> ((Number) row.get("id")).longValue())
                        .toList();
        if (matchingTargetIds.size() > 1) {
            throw new IllegalStateException("Exclusive administrator email is ambiguous");
        }

        long targetAdminId;
        if (matchingTargetIds.isEmpty()) {
            MapSqlParameterSource createParameters =
                    new MapSqlParameterSource()
                            .addValue("name", name)
                            .addValue("email", email)
                            .addValue("password", bcryptPasswordHash);
            int created =
                    jdbcTemplate.update(
                            "INSERT INTO admins "
                                    + "(name, email, phone, status, password, must_change_password, "
                                    + "email_verification_code, email_verification_sent_on) "
                                    + "VALUES (:name, :email, '', 'ACTIVE', :password, 1, '', "
                                    + "CURRENT_TIMESTAMP)",
                            createParameters);
            if (created != 1) {
                throw new IllegalStateException("Exclusive administrator was not created");
            }
            Long createdId =
                    jdbcTemplate.queryForObject(
                            "SELECT id FROM admins WHERE LOWER(email)=:email",
                            new MapSqlParameterSource("email", email),
                            Long.class);
            if (createdId == null) {
                throw new IllegalStateException("Exclusive administrator id was not resolved");
            }
            targetAdminId = createdId;
        } else {
            targetAdminId = matchingTargetIds.get(0);
        }

        MapSqlParameterSource targetParameters =
                new MapSqlParameterSource()
                        .addValue("id", targetAdminId)
                        .addValue("name", name)
                        .addValue("email", email)
                        .addValue("password", bcryptPasswordHash);
        int activated =
                jdbcTemplate.update(
                        "UPDATE admins SET name=:name, email=:email, phone='', status='ACTIVE', "
                                + "password=:password, must_change_password=1, "
                                + "email_verification_code='', "
                                + "email_verification_sent_on=CURRENT_TIMESTAMP "
                                + "WHERE id=:id",
                        targetParameters);
        if (activated != 1) {
            throw new IllegalStateException("Exclusive administrator was not activated");
        }

        jdbcTemplate.update(
                "UPDATE admin_impersonation_sessions SET ended_at=CURRENT_TIMESTAMP, "
                        + "ended_reason='ADMIN_ACCOUNT_REMOVED' WHERE ended_at IS NULL",
                new MapSqlParameterSource());
        jdbcTemplate.update("DELETE FROM admin_mfa_totp", new MapSqlParameterSource());
        jdbcTemplate.update(
                "DELETE FROM password_reset_tokens WHERE entity_type='ADMIN'",
                new MapSqlParameterSource());
        jdbcTemplate.update(
                "DELETE FROM admin_privileges WHERE admin_id=:id", targetParameters);

        Set<String> fullPrivileges = new LinkedHashSet<>(REQUIRED_PRIVILEGES);
        inheritedPrivileges.stream()
                .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
                .filter(value -> value.matches("[A-Z][A-Z0-9_]{2,149}"))
                .forEach(fullPrivileges::add);
        for (String privilege : fullPrivileges) {
            jdbcTemplate.update(
                    "INSERT INTO admin_privileges (admin_id, privilege) "
                            + "VALUES (:admin_id, :privilege)",
                    new MapSqlParameterSource()
                            .addValue("admin_id", targetAdminId)
                            .addValue("privilege", privilege));
        }

        List<Long> revokedAdminIds =
                existingAdmins.stream()
                        .map(row -> ((Number) row.get("id")).longValue())
                        .toList();
        List<Long> removedAdminIds =
                revokedAdminIds.stream().filter(id -> id != targetAdminId).toList();
        int removed =
                jdbcTemplate.update(
                        "DELETE FROM admins WHERE id<>:id", targetParameters);
        if (removed != removedAdminIds.size()) {
            throw new IllegalStateException("Administrator inventory changed during provisioning");
        }

        Integer finalAdminCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM admins", new MapSqlParameterSource(), Integer.class);
        Integer finalPrivilegeCount =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM admin_privileges WHERE admin_id=:id",
                        targetParameters,
                        Integer.class);
        if (finalAdminCount == null
                || finalAdminCount != 1
                || finalPrivilegeCount == null
                || finalPrivilegeCount != fullPrivileges.size()) {
            throw new IllegalStateException("Exclusive administrator verification failed");
        }

        operationParameters
                .addValue("target_admin_id", targetAdminId)
                .addValue("removed_admin_count", removed)
                .addValue("granted_privilege_count", fullPrivileges.size());
        int completed =
                jdbcTemplate.update(
                        "UPDATE admin_bootstrap_operations SET target_admin_id=:target_admin_id, "
                                + "removed_admin_count=:removed_admin_count, "
                                + "granted_privilege_count=:granted_privilege_count, "
                                + "completed_at=CURRENT_TIMESTAMP WHERE operation_id=:operation_id",
                        operationParameters);
        if (completed != 1) {
            throw new IllegalStateException("Exclusive administrator operation was not recorded");
        }

        return new ProvisionResult(
                false,
                targetAdminId,
                removed,
                fullPrivileges.size(),
                new ArrayList<>(revokedAdminIds));
    }

    private String sha256Hex(String input) {
        try {
            byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder(64);
            for (byte item : digest) {
                value.append(String.format("%02x", item));
            }
            return value.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ProvisionResult(
            boolean alreadyProcessed,
            long targetAdminId,
            int removedAdminCount,
            int grantedPrivilegeCount,
            List<Long> revokedAdminIds) {

        static ProvisionResult alreadyProcessed() {
            return new ProvisionResult(true, 0L, 0, 0, List.of());
        }
    }
}
