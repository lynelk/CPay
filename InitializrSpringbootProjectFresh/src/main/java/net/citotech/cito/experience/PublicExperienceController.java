package net.citotech.cito.experience;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(path = "/api/public", produces = "application/json")
public class PublicExperienceController {
    private static final Set<String> ANALYTICS_EVENTS =
            Set.of(
                    "PAGE_VIEW",
                    "CTA_SELECTED",
                    "SIGNUP_STARTED",
                    "SIGNUP_COMPLETED",
                    "SANDBOX_STARTED",
                    "FIRST_TEST_TRANSACTION",
                    "GO_LIVE_REQUESTED",
                    "SUPPORT_CASE_CREATED");

    private final NamedParameterJdbcTemplate jdbc;

    public PublicExperienceController(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        List<Map<String, Object>> incidents =
                jdbc.queryForList(
                        """
                        SELECT incident_reference, provider_code, country_code, channel_code,
                               severity, status, public_title, public_message, started_at, updated_at
                        FROM provider_incidents
                        WHERE environment='PRODUCTION' AND status NOT IN ('RESOLVED','CLOSED')
                        ORDER BY started_at DESC
                        """,
                        new MapSqlParameterSource());
        boolean critical =
                incidents.stream().anyMatch(row -> "CRITICAL".equals(row.get("severity")));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put(
                "status",
                critical ? "MAJOR_OUTAGE" : incidents.isEmpty() ? "OPERATIONAL" : "DEGRADED");
        response.put(
                "components",
                List.of("Cito Gateway", "Cito Payments API", "Merchant Portal", "Admin Portal"));
        response.put("activeIncidents", incidents);
        response.put("generatedAt", Instant.now());
        return response;
    }

    @Transactional
    @PostMapping("/sales-enquiries")
    public Map<String, Object> salesEnquiry(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireRateLimit("public-sales:" + hashOrNull(clientAddress(request)), 5);
        String reference = reference("LEAD");
        MapSqlParameterSource params =
                new MapSqlParameterSource("reference", reference)
                        .addValue("contactName", text(body, "contactName", 2, 190))
                        .addValue("workEmail", email(body.get("workEmail")))
                        .addValue("companyName", text(body, "companyName", 2, 240))
                        .addValue("countryCode", optional(body.get("countryCode"), 3))
                        .addValue("serviceInterest", text(body, "serviceInterest", 2, 120))
                        .addValue("message", optional(body.get("message"), 2000))
                        .addValue("consent", Boolean.TRUE.equals(body.get("consent")))
                        .addValue("sourcePath", optional(body.get("sourcePath"), 500));
        if (!Boolean.TRUE.equals(params.getValue("consent"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Consent is required");
        }
        jdbc.update(
                """
                INSERT INTO sales_enquiries
                    (enquiry_reference,contact_name,work_email,company_name,country_code,
                     service_interest,message,consent_recorded,source_path)
                VALUES (:reference,:contactName,:workEmail,:companyName,:countryCode,
                        :serviceInterest,:message,:consent,:sourcePath)
                """,
                params);
        return Map.of("enquiryReference", reference, "status", "RECEIVED");
    }

    @Transactional
    @PostMapping("/analytics/events")
    public Map<String, Object> analyticsEvent(
            @RequestBody Map<String, Object> body, HttpServletRequest request) {
        requireRateLimit("public-analytics:" + hashOrNull(clientAddress(request)), 120);
        String eventName = text(body, "eventName", 2, 100).toUpperCase(Locale.ROOT);
        if (!ANALYTICS_EVENTS.contains(eventName)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Unsupported analytics event");
        }
        String reference = reference("EVT");
        String sessionReference = request.getHeader("X-Request-ID");
        jdbc.update(
                """
                INSERT INTO product_analytics_events
                    (event_reference,event_name,audience,actor_reference_hash,session_reference_hash,
                     page_path,environment,properties_json)
                VALUES (:reference,:eventName,:audience,:actorHash,:sessionHash,:pagePath,:environment,:properties)
                """,
                new MapSqlParameterSource("reference", reference)
                        .addValue("eventName", eventName)
                        .addValue(
                                "audience",
                                optional(body.get("audience"), 30) == null
                                        ? "PUBLIC"
                                        : optional(body.get("audience"), 30))
                        .addValue(
                                "actorHash", hashOrNull(optional(body.get("actorReference"), 190)))
                        .addValue("sessionHash", hashOrNull(sessionReference))
                        .addValue("pagePath", optional(body.get("pagePath"), 500))
                        .addValue("environment", environment(body.get("environment")))
                        .addValue("properties", "{}"));
        return Map.of("accepted", true, "eventReference", reference);
    }

    private String text(Map<String, Object> body, String field, int min, int max) {
        String value = body.get(field) == null ? "" : String.valueOf(body.get(field)).trim();
        if (value.length() < min || value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid " + field);
        }
        return value;
    }

    private String optional(Object raw, int max) {
        if (raw == null || String.valueOf(raw).isBlank()) {
            return null;
        }
        String value = String.valueOf(raw).trim();
        if (value.length() > max) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Field is too long");
        }
        return value;
    }

    private String email(Object raw) {
        String value = raw == null ? "" : String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (value.length() > 190 || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A valid work email is required");
        }
        return value;
    }

    private String environment(Object raw) {
        String value =
                raw == null ? "SANDBOX" : String.valueOf(raw).trim().toUpperCase(Locale.ROOT);
        if (!Set.of("SANDBOX", "PRODUCTION").contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported environment");
        }
        return value;
    }

    private String hashOrNull(String value) {
        return value == null ? null : ProductExperienceController.hash(value);
    }

    private void requireRateLimit(String key, int maximumPerMinute) {
        Instant window = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        MapSqlParameterSource params =
                new MapSqlParameterSource("rateKey", key)
                        .addValue("windowStart", Timestamp.from(window));
        try {
            jdbc.update(
                    "INSERT INTO api_rate_limits (rate_key,window_start,request_count) VALUES (:rateKey,:windowStart,1)",
                    params);
        } catch (DuplicateKeyException duplicate) {
            jdbc.update(
                    "UPDATE api_rate_limits SET request_count=request_count+1 WHERE rate_key=:rateKey AND window_start=:windowStart",
                    params);
        }
        Integer count =
                jdbc.queryForObject(
                        "SELECT request_count FROM api_rate_limits WHERE rate_key=:rateKey AND window_start=:windowStart",
                        params,
                        Integer.class);
        if (count != null && count > maximumPerMinute) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Request rate limit exceeded");
        }
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private String reference(String prefix) {
        return prefix
                + "-"
                + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 20)
                        .toUpperCase(Locale.ROOT);
    }
}
