package net.citotech.cito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provider-neutral compliance screening service.
 *
 * <p>The registry persists provider configuration and records every screening request before
 * returning an adapter result. A local adapter is always available for tests and sandbox
 * environments; live vendors require explicit configuration and adapter registration.
 */
@Service
public class ScreeningProviderAdapterRegistry {

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, ScreeningProviderAdapter> adapters;

    public ScreeningProviderAdapterRegistry(
            JdbcTemplate jdbcTemplate, List<ScreeningProviderAdapter> adapters) {
        this.jdbcTemplate = jdbcTemplate;
        this.adapters =
                adapters.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        adapter -> normalize(adapter.providerCode()),
                                        Function.identity()));
    }

    @Transactional
    public ScreeningResult screen(ScreeningRequest request) {
        ScreeningRequest validated = request.validate();
        ProviderConfig config =
                findProvider(validated.providerCode())
                        .orElseGet(() -> defaultProvider(validated.providerCode()));
        ScreeningProviderAdapter adapter =
                adapters.getOrDefault(
                        normalize(config.providerCode()),
                        adapters.get(normalize(LocalScreeningProviderAdapter.PROVIDER_CODE)));
        if (adapter == null) {
            throw new IllegalStateException("No local screening adapter is registered");
        }

        ScreeningResult adapterResult = adapter.screen(validated, config);
        jdbcTemplate.update(
                "insert into screening_provider_requests "
                        + "(provider_code, subject_type, subject_reference, screening_type, request_status, risk_level, "
                        + "match_count, external_reference, request_payload, response_payload, requested_by) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                config.providerCode(),
                validated.subjectType(),
                validated.subjectReference(),
                validated.screeningType(),
                adapterResult.status(),
                adapterResult.riskLevel(),
                adapterResult.matchCount(),
                adapterResult.externalReference(),
                validated.payload(),
                adapterResult.responsePayload(),
                validated.requestedBy());

        return new ScreeningResult(
                lastInsertId(),
                adapterResult.status(),
                adapterResult.riskLevel(),
                adapterResult.matchCount(),
                adapterResult.externalReference(),
                adapterResult.responsePayload());
    }

    public Optional<ProviderConfig> findProvider(String providerCode) {
        return jdbcTemplate
                .query(
                        "select provider_code, display_name, environment, enabled, supports_sanctions, supports_pep, "
                                + "supports_document_verification, supports_beneficiary_screening, supports_merchant_screening "
                                + "from screening_provider_configs where provider_code = ?",
                        (rs, rowNum) ->
                                new ProviderConfig(
                                        rs.getString("provider_code"),
                                        rs.getString("display_name"),
                                        rs.getString("environment"),
                                        rs.getBoolean("enabled"),
                                        rs.getBoolean("supports_sanctions"),
                                        rs.getBoolean("supports_pep"),
                                        rs.getBoolean("supports_document_verification"),
                                        rs.getBoolean("supports_beneficiary_screening"),
                                        rs.getBoolean("supports_merchant_screening")),
                        normalize(providerCode))
                .stream()
                .findFirst();
    }

    @Transactional
    public Map<String, Object> registerProvider(Map<String, Object> payload) {
        String providerCode = normalize(required(payload, "providerCode"));
        String displayName = value(payload, "displayName", providerCode);
        String environment = normalize(value(payload, "environment", "SANDBOX"));
        String authMode = normalize(value(payload, "authMode", "NONE"));
        boolean enabled = bool(payload, "enabled");

        jdbcTemplate.update(
                "insert into screening_provider_configs "
                        + "(provider_code, display_name, environment, base_url, auth_mode, enabled, supports_sanctions, "
                        + "supports_pep, supports_document_verification, supports_beneficiary_screening, supports_merchant_screening) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "on duplicate key update "
                        + "display_name = values(display_name), environment = values(environment), base_url = values(base_url), "
                        + "auth_mode = values(auth_mode), enabled = values(enabled), supports_sanctions = values(supports_sanctions), "
                        + "supports_pep = values(supports_pep), supports_document_verification = values(supports_document_verification), "
                        + "supports_beneficiary_screening = values(supports_beneficiary_screening), "
                        + "supports_merchant_screening = values(supports_merchant_screening), updated_at = current_timestamp",
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
                bool(payload, "supportsMerchantScreening"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("providerCode", providerCode);
        result.put("status", enabled ? "ENABLED" : "REGISTERED_DISABLED");
        result.put("environment", environment);
        result.put("adapterRegistered", adapters.containsKey(providerCode));
        return result;
    }

    private ProviderConfig defaultProvider(String providerCode) {
        String normalized = normalize(providerCode);
        jdbcTemplate.update(
                "insert into screening_provider_configs (provider_code, display_name, environment, enabled) "
                        + "values (?, ?, 'SANDBOX', false) "
                        + "on duplicate key update provider_code = provider_code",
                normalized,
                normalized);
        return new ProviderConfig(
                normalized, normalized, "SANDBOX", false, false, false, false, false, false);
    }

    private Long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
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

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("providerCode is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record ScreeningRequest(
            String providerCode,
            String subjectType,
            String subjectReference,
            String screeningType,
            String payload,
            String requestedBy) {
        ScreeningRequest validate() {
            return new ScreeningRequest(
                    normalize(providerCode),
                    require(subjectType, "subjectType"),
                    require(subjectReference, "subjectReference"),
                    require(screeningType, "screeningType"),
                    payload == null || payload.isBlank() ? "{}" : payload,
                    requestedBy == null || requestedBy.isBlank() ? "system" : requestedBy);
        }

        private static String require(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " is required");
            }
            return value;
        }
    }

    public record ScreeningResult(
            Long requestId,
            String status,
            String riskLevel,
            int matchCount,
            String externalReference,
            String responsePayload) {
        public ScreeningResult(
                Long requestId,
                String status,
                String riskLevel,
                int matchCount,
                String externalReference) {
            this(requestId, status, riskLevel, matchCount, externalReference, "{}");
        }
    }

    public record ProviderConfig(
            String providerCode,
            String displayName,
            String environment,
            boolean enabled,
            boolean supportsSanctions,
            boolean supportsPep,
            boolean supportsDocumentVerification,
            boolean supportsBeneficiaryScreening,
            boolean supportsMerchantScreening) {}
}
