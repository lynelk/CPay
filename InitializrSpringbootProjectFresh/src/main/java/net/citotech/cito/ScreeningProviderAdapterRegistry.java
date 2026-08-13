package net.citotech.cito;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Provider-neutral compliance screening registry.
 *
 * <p>The registry records screening requests and exposes a small adapter contract so CPay can plug in
 * sanctions, PEP, document verification and beneficiary-screening vendors without hardcoding a single
 * provider into the compliance workflow. The default implementation records a PENDING_PROVIDER result
 * and is deliberately safe for sandbox environments where a live screening vendor is not yet configured.
 */
@Component
public class ScreeningProviderAdapterRegistry {

    private final JdbcTemplate jdbcTemplate;

    public ScreeningProviderAdapterRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ScreeningResult screen(ScreeningRequest request) {
        ProviderConfig config = findProvider(request.providerCode()).orElseGet(() -> defaultProvider(request.providerCode()));
        String status = config.enabled() ? "SUBMITTED" : "PENDING_PROVIDER";
        String riskLevel = config.enabled() ? "UNKNOWN" : "NOT_SCREENED";
        String externalReference = "SCR-" + Instant.now().toEpochMilli();

        Long requestId = jdbcTemplate.queryForObject(
            "insert into screening_provider_requests "
                + "(provider_code, subject_type, subject_reference, screening_type, request_status, risk_level, "
                + " match_count, external_reference, request_payload, requested_by) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id",
            Long.class,
            request.providerCode(),
            request.subjectType(),
            request.subjectReference(),
            request.screeningType(),
            status,
            riskLevel,
            0,
            externalReference,
            request.payload(),
            request.requestedBy()
        );

        return new ScreeningResult(requestId, status, riskLevel, 0, externalReference);
    }

    public Optional<ProviderConfig> findProvider(String providerCode) {
        return jdbcTemplate.query(
                "select provider_code, display_name, environment, enabled, supports_sanctions, supports_pep, "
                    + "supports_document_verification, supports_beneficiary_screening, supports_merchant_screening "
                    + "from screening_provider_configs where provider_code = ?",
                (rs, rowNum) -> new ProviderConfig(
                    rs.getString("provider_code"),
                    rs.getString("display_name"),
                    rs.getString("environment"),
                    rs.getBoolean("enabled"),
                    rs.getBoolean("supports_sanctions"),
                    rs.getBoolean("supports_pep"),
                    rs.getBoolean("supports_document_verification"),
                    rs.getBoolean("supports_beneficiary_screening"),
                    rs.getBoolean("supports_merchant_screening")
                ),
                providerCode
            )
            .stream()
            .findFirst();
    }

    public Map<String, Object> registerProvider(Map<String, Object> payload) {
        String providerCode = required(payload, "providerCode");
        String displayName = value(payload, "displayName", providerCode);
        String environment = value(payload, "environment", "SANDBOX");
        String authMode = value(payload, "authMode", "NONE");
        boolean enabled = Boolean.parseBoolean(value(payload, "enabled", "false"));

        jdbcTemplate.update(
            "insert into screening_provider_configs "
                + "(provider_code, display_name, environment, base_url, auth_mode, enabled, supports_sanctions, "
                + " supports_pep, supports_document_verification, supports_beneficiary_screening, supports_merchant_screening) "
                + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "on conflict (provider_code) do update set "
                + "display_name = excluded.display_name, environment = excluded.environment, base_url = excluded.base_url, "
                + "auth_mode = excluded.auth_mode, enabled = excluded.enabled, supports_sanctions = excluded.supports_sanctions, "
                + "supports_pep = excluded.supports_pep, supports_document_verification = excluded.supports_document_verification, "
                + "supports_beneficiary_screening = excluded.supports_beneficiary_screening, "
                + "supports_merchant_screening = excluded.supports_merchant_screening, updated_at = current_timestamp",
            providerCode,
            displayName,
            environment,
            value(payload, "baseUrl", null),
            authMode,
            enabled,
            bool(payload, "supportsSanctions"),
            bool(payload, "supportsPep"),
            bool(payload, "supportsDocumentVerification"),
            bool(payload, "supportsBeneficiaryScreening"),
            bool(payload, "supportsMerchantScreening")
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerCode", providerCode);
        result.put("status", enabled ? "ENABLED" : "REGISTERED_DISABLED");
        result.put("environment", environment);
        return result;
    }

    private ProviderConfig defaultProvider(String providerCode) {
        jdbcTemplate.update(
            "insert into screening_provider_configs (provider_code, display_name, environment, enabled) "
                + "values (?, ?, 'SANDBOX', false) on conflict (provider_code) do nothing",
            providerCode,
            providerCode
        );
        return new ProviderConfig(providerCode, providerCode, "SANDBOX", false, false, false, false, false, false);
    }

    private static String required(Map<String, Object> payload, String key) {
        String value = value(payload, key, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static String value(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload == null ? null : payload.get(key);
        return value == null ? defaultValue : value.toString();
    }

    private static boolean bool(Map<String, Object> payload, String key) {
        Object value = payload == null ? null : payload.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    public record ScreeningRequest(
        String providerCode,
        String subjectType,
        String subjectReference,
        String screeningType,
        String payload,
        String requestedBy
    ) {}

    public record ScreeningResult(
        Long requestId,
        String status,
        String riskLevel,
        int matchCount,
        String externalReference
    ) {}

    public record ProviderConfig(
        String providerCode,
        String displayName,
        String environment,
        boolean enabled,
        boolean supportsSanctions,
        boolean supportsPep,
        boolean supportsDocumentVerification,
        boolean supportsBeneficiaryScreening,
        boolean supportsMerchantScreening
    ) {}
}
