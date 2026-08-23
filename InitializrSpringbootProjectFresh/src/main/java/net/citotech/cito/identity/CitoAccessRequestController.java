package net.citotech.cito.identity;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.citotech.cito.security.LoginRateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public intake for Cito privileged-access requests.
 *
 * <p>This endpoint deliberately does not create users, set passwords, assign roles, or approve
 * access. It only records a PENDING request for an authorized reviewer. Merchant self-service
 * registration continues to use the dedicated merchant onboarding flow.</p>
 */
@RestController
@RequestMapping("/api/public/access-requests")
public class CitoAccessRequestController {

    private static final Set<String> ALLOWED_ACCESS_TYPES = Set.of(
            "ADMINISTRATION", "OPERATIONS", "FINANCE", "COMPLIANCE", "PARTNER", "OTHER");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final LoginRateLimiter rateLimiter;

    public CitoAccessRequestController(
            NamedParameterJdbcTemplate jdbcTemplate,
            LoginRateLimiter rateLimiter) {
        this.jdbcTemplate = jdbcTemplate;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> requestAccess(
            @RequestBody AccessRequest request,
            HttpServletRequest servletRequest) {
        ValidationResult validated = validate(request);
        if (!validated.valid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "accepted", false,
                    "message", validated.message()));
        }

        String clientIp = servletRequest.getRemoteAddr();
        if (!rateLimiter.tryConsume(validated.workEmail(), clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of(
                    "accepted", false,
                    "message", "Too many access requests. Please try again later."));
        }

        MapSqlParameterSource duplicateParams = new MapSqlParameterSource()
                .addValue("work_email", validated.workEmail())
                .addValue("requested_access_type", validated.requestedAccessType());
        Integer pendingCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cito_access_requests "
                        + "WHERE work_email=:work_email AND requested_access_type=:requested_access_type "
                        + "AND status='PENDING'",
                duplicateParams,
                Integer.class);

        if (pendingCount == null || pendingCount == 0) {
            MapSqlParameterSource params = new MapSqlParameterSource()
                    .addValue("request_reference", "CAR-" + UUID.randomUUID())
                    .addValue("full_name", validated.fullName())
                    .addValue("work_email", validated.workEmail())
                    .addValue("organization", validated.organization())
                    .addValue("requested_access_type", validated.requestedAccessType())
                    .addValue("reason", validated.reason())
                    .addValue("source_ip_hash", sha256(clientIp == null ? "" : clientIp));
            jdbcTemplate.update(
                    "INSERT INTO cito_access_requests "
                            + "(request_reference, full_name, work_email, organization, requested_access_type, reason, status, source_ip_hash, created_at) "
                            + "VALUES (:request_reference, :full_name, :work_email, :organization, :requested_access_type, :reason, 'PENDING', :source_ip_hash, :created_at)",
                    params.addValue("created_at", java.sql.Timestamp.from(Instant.now())));
        }

        // Deliberately generic for both new and duplicate requests to avoid account/request enumeration.
        return ResponseEntity.accepted().body(Map.of(
                "accepted", true,
                "status", "PENDING",
                "message", "Request received. Access is not provisioned until an authorized administrator reviews and approves it."));
    }

    private ValidationResult validate(AccessRequest request) {
        if (request == null) {
            return ValidationResult.invalid("Request details are required.");
        }
        String fullName = clean(request.fullName());
        String workEmail = clean(request.workEmail()).toLowerCase(Locale.ROOT);
        String organization = clean(request.organization());
        String accessType = clean(request.requestedAccessType()).toUpperCase(Locale.ROOT);
        String reason = clean(request.reason());

        if (fullName.length() < 2 || fullName.length() > 160) {
            return ValidationResult.invalid("Full name must be between 2 and 160 characters.");
        }
        if (workEmail.length() > 254 || !EMAIL_PATTERN.matcher(workEmail).matches()) {
            return ValidationResult.invalid("A valid work email address is required.");
        }
        if (organization.length() < 2 || organization.length() > 200) {
            return ValidationResult.invalid("Organization must be between 2 and 200 characters.");
        }
        if (!ALLOWED_ACCESS_TYPES.contains(accessType)) {
            return ValidationResult.invalid("Requested access type is not supported.");
        }
        if (reason.length() < 10 || reason.length() > 2000) {
            return ValidationResult.invalid("Reason must be between 10 and 2000 characters.");
        }
        return new ValidationResult(true, "", fullName, workEmail, organization, accessType, reason);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record AccessRequest(
            String fullName,
            String workEmail,
            String organization,
            String requestedAccessType,
            String reason) {}

    private record ValidationResult(
            boolean valid,
            String message,
            String fullName,
            String workEmail,
            String organization,
            String requestedAccessType,
            String reason) {
        static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, "", "", "", "", "");
        }
    }
}
