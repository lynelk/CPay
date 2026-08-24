package net.citotech.cito.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.Common;
import net.citotech.cito.admin.FeatureFlagService;
import net.citotech.cito.admin.FeatureKeys;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Identity verification orchestrator.
 *
 * <p>Consent is mandatory before any provider call. Identity values are persisted only as a hash
 * and masked fragment; the raw value exists only for the duration of the provider request. The
 * service supports multiple identity-document types while preserving the original Uganda NIN
 * contract for existing callers and records.
 */
@Service
public class IdentityVerificationService {

    private static final String DEFAULT_STATUS = "PENDING";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final FeatureFlagService featureFlagService;
    private final List<IdentityVerificationConnector> connectors;

    public IdentityVerificationService(
            NamedParameterJdbcTemplate jdbcTemplate,
            FeatureFlagService featureFlagService,
            List<IdentityVerificationConnector> connectors) {
        this.jdbcTemplate = jdbcTemplate;
        this.featureFlagService = featureFlagService;
        this.connectors = connectors;
    }

    /** Backward-compatible Uganda NIN entry point. */
    @Transactional
    public Map<String, Object> verify(
            long merchantId,
            String nin,
            String fullName,
            String msisdn,
            boolean consentGranted,
            String requestedBy) {
        return verify(
                merchantId,
                "NIN",
                "UG",
                nin,
                fullName,
                msisdn,
                consentGranted,
                requestedBy);
    }

    /** Generic identity-document verification entry point. */
    @Transactional
    public Map<String, Object> verify(
            long merchantId,
            String identityType,
            String country,
            String identityNumber,
            String fullName,
            String msisdn,
            boolean consentGranted,
            String requestedBy) {
        if (!featureFlagService.isEnabled(FeatureKeys.IDENTITY_GNUGRID)) {
            throw new PaymentGatewayException(
                    "Identity verification is not enabled (feature flag identity-gnugrid is off).");
        }

        String normalizedType = normalizeCode(identityType, "NIN");
        String normalizedCountry = normalizeCode(country, "UG");
        String normalizedIdentityNumber = normalizeIdentityNumber(identityNumber);
        if (normalizedIdentityNumber.isEmpty()) {
            throw new PaymentGatewayException("identityNumber is required.");
        }
        if (!consentGranted) {
            throw new PaymentGatewayException(
                    "Identity verification requires the subject's consent.");
        }

        IdentityVerificationConnector connector = findConnector(normalizedType, normalizedCountry);
        String reference = "IDV-" + Common.randomUrlSafeToken(16);
        String identityHash = identityHash(normalizedType, normalizedCountry, normalizedIdentityNumber);
        String identityMask = maskIdentity(normalizedIdentityNumber);
        String nameMask = GnuGridConnector.maskName(fullName);
        String msisdnMask = GnuGridConnector.maskMsisdn(msisdn);

        insertRequest(
                reference,
                merchantId,
                normalizedType,
                normalizedCountry,
                nameMask,
                msisdnMask,
                identityHash,
                identityMask,
                consentGranted,
                requestedBy);
        audit(
                reference,
                merchantId,
                "REQUEST_CREATED",
                requestedBy,
                "consent recorded; identityType=" + normalizedType + "; country=" + normalizedCountry);

        IdentityRecords.IdentityVerificationRequest providerRequest =
                new IdentityRecords.IdentityVerificationRequest(
                        reference,
                        merchantId,
                        normalizedType,
                        normalizedCountry,
                        normalizedIdentityNumber,
                        fullName,
                        msisdn);
        IdentityRecords.VerifiedIdentity result = connector.verify(providerRequest);

        upsertProfile(
                merchantId,
                normalizedType,
                normalizedCountry,
                identityHash,
                identityMask,
                result.match() ? hashOrNull(fullName) : null,
                result.match() ? nameMask : null,
                result.match() ? hashOrNull(msisdn) : null,
                result.match() ? msisdnMask : null,
                result.verificationStatus(),
                connector.providerCode(),
                result.providerReference(),
                result.verifiedAt(),
                result.expiresAt());
        completeRequest(
                reference,
                result.verificationStatus(),
                result.providerReference(),
                result.rawProviderResult());
        audit(reference, merchantId, "RESULT_RECORDED", requestedBy, result.verificationStatus());

        return toRequestView(
                reference,
                merchantId,
                normalizedType,
                normalizedCountry,
                nameMask,
                msisdnMask,
                identityHash,
                identityMask,
                result.verificationStatus(),
                result.providerReference(),
                requestedBy);
    }

    public Map<String, Object> findRequestByReference(String reference) {
        String sql =
                "SELECT id, request_reference, merchant_id, identity_type, country_code, "
                        + "subject_name, subject_msisdn, identity_number_hash, identity_number_mask, "
                        + "consent_granted, consent_recorded_by, consent_recorded_at, request_status, "
                        + "provider_reference, requested_by, created_at, updated_at "
                        + "FROM identity_verification_requests WHERE request_reference=:reference";
        MapSqlParameterSource p = new MapSqlParameterSource("reference", reference);
        try {
            return jdbcTemplate.queryForMap(sql, p);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public List<Map<String, Object>> listRequests(long merchantId) {
        MapSqlParameterSource p = new MapSqlParameterSource("merchant_id", merchantId);
        return jdbcTemplate.queryForList(
                "SELECT id, request_reference, merchant_id, identity_type, country_code, "
                        + "subject_name, subject_msisdn, identity_number_mask, consent_granted, "
                        + "consent_recorded_by, consent_recorded_at, request_status, "
                        + "provider_reference, requested_by, created_at, updated_at "
                        + "FROM identity_verification_requests WHERE merchant_id=:merchant_id "
                        + "ORDER BY id DESC LIMIT 200",
                p);
    }

    /** Backward-compatible lookup for existing Uganda NIN consumers. */
    public Map<String, Object> findVerifiedByNin(String nin) {
        return findVerifiedIdentity(null, "NIN", "UG", nin);
    }

    public Map<String, Object> findVerifiedIdentity(
            Long merchantId, String identityType, String country, String identityNumber) {
        String normalizedType = normalizeCode(identityType, "NIN");
        String normalizedCountry = normalizeCode(country, "UG");
        String normalizedIdentityNumber = normalizeIdentityNumber(identityNumber);
        if (normalizedIdentityNumber.isEmpty()) {
            return null;
        }
        String hash = identityHash(normalizedType, normalizedCountry, normalizedIdentityNumber);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("hash", hash);
        p.addValue("identity_type", normalizedType);
        p.addValue("country_code", normalizedCountry);
        if (merchantId != null) p.addValue("merchant_id", merchantId);
        String merchantClause = merchantId == null ? "" : " AND merchant_id=:merchant_id";
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, merchant_id, identity_type, country_code, identity_number_mask, "
                            + "full_name_mask, msisdn_mask, verification_status, verified_service, "
                            + "provider_reference, verified_at, expires_at, updated_at "
                            + "FROM verified_profiles WHERE identity_number_hash=:hash "
                            + "AND identity_type=:identity_type AND country_code=:country_code"
                            + merchantClause
                            + " ORDER BY id DESC LIMIT 1",
                    p);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void insertRequest(
            String reference,
            long merchantId,
            String identityType,
            String country,
            String nameMask,
            String msisdnMask,
            String identityHash,
            String identityMask,
            boolean consentGranted,
            String requestedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("merchant_id", merchantId);
        p.addValue("identity_type", identityType);
        p.addValue("country_code", country);
        p.addValue("subject_name", emptyToNull(nameMask));
        p.addValue("subject_msisdn", emptyToNull(msisdnMask));
        p.addValue("identity_hash", identityHash);
        p.addValue("identity_mask", identityMask);
        p.addValue("consent", consentGranted ? "YES" : "NO");
        p.addValue("consent_by", emptyToNull(requestedBy));
        p.addValue("requested_by", emptyToNull(requestedBy));
        jdbcTemplate.update(
                "INSERT INTO identity_verification_requests "
                        + "(request_reference, merchant_id, identity_type, country_code, subject_name, "
                        + "subject_msisdn, identity_number_hash, identity_number_mask, consent_granted, "
                        + "consent_recorded_by, consent_recorded_at, request_status, requested_by) "
                        + "VALUES (:reference, :merchant_id, :identity_type, :country_code, "
                        + ":subject_name, :subject_msisdn, :identity_hash, :identity_mask, :consent, "
                        + ":consent_by, CURRENT_TIMESTAMP, 'PENDING', :requested_by)",
                p);
    }

    private void completeRequest(
            String reference, String status, String providerReference, String rawResult) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("status", status == null ? DEFAULT_STATUS : status);
        p.addValue("provider_reference", emptyToNull(providerReference));
        p.addValue("result_json", emptyToNull(rawResult));
        jdbcTemplate.update(
                "UPDATE identity_verification_requests "
                        + "SET request_status=:status, provider_reference=:provider_reference, "
                        + "provider_result_json=:result_json, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE request_reference=:reference",
                p);
    }

    private void upsertProfile(
            long merchantId,
            String identityType,
            String country,
            String identityHash,
            String identityMask,
            String nameHash,
            String nameMask,
            String msisdnHash,
            String msisdnMask,
            String status,
            String providerCode,
            String providerReference,
            Instant verifiedAt,
            Instant expiresAt) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("identity_type", identityType);
        p.addValue("country_code", country);
        p.addValue("identity_hash", identityHash);
        p.addValue("identity_mask", identityMask);
        p.addValue("name_hash", emptyToNull(nameHash));
        p.addValue("name_mask", emptyToNull(nameMask));
        p.addValue("msisdn_hash", emptyToNull(msisdnHash));
        p.addValue("msisdn_mask", emptyToNull(msisdnMask));
        p.addValue("status", status == null ? DEFAULT_STATUS : status);
        p.addValue("provider_code", emptyToNull(providerCode));
        p.addValue("provider_reference", emptyToNull(providerReference));
        p.addValue("verified_at", verifiedAt == null ? null : Timestamp.from(verifiedAt));
        p.addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt));
        jdbcTemplate.update(
                "INSERT INTO verified_profiles "
                        + "(merchant_id, identity_type, country_code, identity_number_hash, "
                        + "identity_number_mask, full_name_hash, full_name_mask, msisdn_hash, "
                        + "msisdn_mask, verification_status, verified_service, provider_reference, "
                        + "verified_at, expires_at) "
                        + "VALUES (:merchant_id, :identity_type, :country_code, :identity_hash, "
                        + ":identity_mask, :name_hash, :name_mask, :msisdn_hash, :msisdn_mask, "
                        + ":status, :provider_code, :provider_reference, :verified_at, :expires_at) "
                        + "ON DUPLICATE KEY UPDATE full_name_hash=:name_hash, full_name_mask=:name_mask, "
                        + "msisdn_hash=:msisdn_hash, msisdn_mask=:msisdn_mask, "
                        + "identity_number_mask=:identity_mask, verification_status=:status, "
                        + "verified_service=:provider_code, provider_reference=:provider_reference, "
                        + "verified_at=:verified_at, expires_at=:expires_at, updated_at=CURRENT_TIMESTAMP",
                p);
    }

    private void audit(
            String reference, long merchantId, String action, String performedBy, String notes) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("merchant_id", merchantId);
        p.addValue("action", action);
        p.addValue("performed_by", emptyToNull(performedBy));
        p.addValue("notes", emptyToNull(notes));
        jdbcTemplate.update(
                "INSERT INTO identity_verification_audit "
                        + "(request_reference, merchant_id, action_name, performed_by, notes) "
                        + "VALUES (:reference, :merchant_id, :action, :performed_by, :notes)",
                p);
    }

    private IdentityVerificationConnector findConnector(String identityType, String country) {
        return connectors.stream()
                .filter(c -> c.supports(identityType, country))
                .findFirst()
                .orElseThrow(
                        () ->
                                new PaymentGatewayException(
                                        "No configured identity provider supports "
                                                + identityType
                                                + " in "
                                                + country
                                                + "."));
    }

    private Map<String, Object> toRequestView(
            String reference,
            long merchantId,
            String identityType,
            String country,
            String nameMask,
            String msisdnMask,
            String identityHash,
            String identityMask,
            String status,
            String providerReference,
            String requestedBy) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requestReference", reference);
        view.put("merchantId", merchantId);
        view.put("identityType", identityType);
        view.put("country", country);
        view.put("subjectNameMasked", nameMask);
        view.put("subjectMsisdnMasked", msisdnMask);
        view.put("identityNumberHash", identityHash);
        view.put("identityNumberMask", identityMask);
        view.put("status", status);
        view.put("providerReference", providerReference == null ? "" : providerReference);
        view.put("requestedBy", requestedBy == null ? "" : requestedBy);
        return view;
    }

    private String identityHash(String identityType, String country, String identityNumber) {
        if ("NIN".equals(identityType) && "UG".equals(country)) {
            return GnuGridConnector.sha256Hex(identityNumber);
        }
        return GnuGridConnector.sha256Hex(identityType + "|" + country + "|" + identityNumber);
    }

    private String maskIdentity(String identityNumber) {
        String normalized = normalizeIdentityNumber(identityNumber);
        if (normalized.length() <= 4) {
            return "*".repeat(Math.max(1, normalized.length()));
        }
        return normalized.substring(0, 2)
                + "*".repeat(normalized.length() - 4)
                + normalized.substring(normalized.length() - 2);
    }

    private String normalizeCode(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? fallback : normalized;
    }

    private String normalizeIdentityNumber(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String hashOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return GnuGridConnector.sha256Hex(value);
    }

    private String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
