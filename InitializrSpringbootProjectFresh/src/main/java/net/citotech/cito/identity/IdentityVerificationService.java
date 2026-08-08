package net.citotech.cito.identity;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
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
 * Identity verification orchestrator (S5 pilot).
 *
 * <p>Consent is mandatory before any provider call: the request row records who granted consent and
 * when, and nothing is sent to the provider otherwise. The NIN is stored only as a SHA-256 hash
 * plus a masked fragment; full name and MSISDN are masked in both tables. The audit trail records
 * every request/result transition, and the whole feature is gated by the {@code identity-gnugrid}
 * feature flag (V36), so an operator must deliberately enable the pilot.
 */
@Service
public class IdentityVerificationService {

    private static final String PROVIDER = "gnugrid";
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

    @Transactional
    public Map<String, Object> verify(
            long merchantId,
            String nin,
            String fullName,
            String msisdn,
            boolean consentGranted,
            String requestedBy) {
        if (!featureFlagService.isEnabled(FeatureKeys.IDENTITY_GNUGRID)) {
            throw new PaymentGatewayException(
                    "Identity verification is not enabled (feature flag identity-gnugrid is off).");
        }
        if (nin == null || nin.trim().isEmpty()) {
            throw new PaymentGatewayException("nin is required.");
        }
        if (!consentGranted) {
            throw new PaymentGatewayException(
                    "Identity verification requires the subject's consent.");
        }
        IdentityVerificationConnector connector = findConnector(PROVIDER);

        String reference = "IDV-" + Common.randomUrlSafeToken(16);
        String ninHash = GnuGridConnector.sha256Hex(nin);
        String ninMask = maskNin(nin);
        String nameMask = GnuGridConnector.maskName(fullName);
        String msisdnMask = GnuGridConnector.maskMsisdn(msisdn);

        insertRequest(
                reference,
                merchantId,
                nameMask,
                msisdnMask,
                ninHash,
                ninMask,
                consentGranted,
                requestedBy);
        audit(reference, merchantId, "REQUEST_CREATED", requestedBy, "consent recorded");

        IdentityRecords.IdentityVerificationRequest providerRequest =
                new IdentityRecords.IdentityVerificationRequest(
                        reference, merchantId, nin, fullName, msisdn);
        IdentityRecords.VerifiedIdentity result = connector.verify(providerRequest);

        upsertProfile(
                merchantId,
                ninHash,
                ninMask,
                result.match() ? hashOrNull(fullName) : null,
                result.match() ? nameMask : null,
                result.match() ? hashOrNull(msisdn) : null,
                result.match() ? msisdnMask : null,
                result.verificationStatus(),
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
                nameMask,
                msisdnMask,
                ninHash,
                ninMask,
                result.verificationStatus(),
                result.providerReference(),
                requestedBy);
    }

    public Map<String, Object> findRequestByReference(String reference) {
        String sql =
                "SELECT id, request_reference, merchant_id, subject_name, subject_msisdn, "
                        + "identity_number_hash, identity_number_mask, consent_granted, "
                        + "consent_recorded_by, consent_recorded_at, request_status, "
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
                "SELECT id, request_reference, merchant_id, subject_name, subject_msisdn, "
                        + "identity_number_mask, consent_granted, consent_recorded_by, "
                        + "consent_recorded_at, request_status, provider_reference, requested_by, "
                        + "created_at, updated_at "
                        + "FROM identity_verification_requests WHERE merchant_id=:merchant_id "
                        + "ORDER BY id DESC LIMIT 200",
                p);
    }

    public Map<String, Object> findVerifiedByNin(String nin) {
        if (nin == null || nin.trim().isEmpty()) {
            return null;
        }
        String hash = GnuGridConnector.sha256Hex(nin);
        MapSqlParameterSource p = new MapSqlParameterSource("hash", hash);
        try {
            return jdbcTemplate.queryForMap(
                    "SELECT id, merchant_id, identity_number_mask, full_name_mask, "
                            + "msisdn_mask, verification_status, verified_service, "
                            + "provider_reference, verified_at, expires_at, updated_at "
                            + "FROM verified_profiles WHERE identity_number_hash=:hash "
                            + "ORDER BY id DESC LIMIT 1",
                    p);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private void insertRequest(
            String reference,
            long merchantId,
            String nameMask,
            String msisdnMask,
            String ninHash,
            String ninMask,
            boolean consentGranted,
            String requestedBy) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("merchant_id", merchantId);
        p.addValue("subject_name", emptyToNull(nameMask));
        p.addValue("subject_msisdn", emptyToNull(msisdnMask));
        p.addValue("nin_hash", ninHash);
        p.addValue("nin_mask", ninMask);
        p.addValue("consent", consentGranted ? "YES" : "NO");
        p.addValue("consent_by", emptyToNull(requestedBy));
        p.addValue("requested_by", emptyToNull(requestedBy));
        jdbcTemplate.update(
                "INSERT INTO identity_verification_requests "
                        + "(request_reference, merchant_id, subject_name, subject_msisdn, "
                        + "identity_number_hash, identity_number_mask, consent_granted, "
                        + "consent_recorded_by, consent_recorded_at, request_status, requested_by) "
                        + "VALUES (:reference, :merchant_id, :subject_name, :subject_msisdn, "
                        + ":nin_hash, :nin_mask, :consent, :consent_by, CURRENT_TIMESTAMP, "
                        + "'PENDING', :requested_by)",
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
                        + "provider_result_json=:result_json, "
                        + "updated_at=CURRENT_TIMESTAMP WHERE request_reference=:reference",
                p);
    }

    private void upsertProfile(
            long merchantId,
            String ninHash,
            String ninMask,
            String nameHash,
            String nameMask,
            String msisdnHash,
            String msisdnMask,
            String status,
            String providerReference,
            Instant verifiedAt,
            Instant expiresAt) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("nin_hash", ninHash);
        p.addValue("nin_mask", ninMask);
        p.addValue("name_hash", emptyToNull(nameHash));
        p.addValue("name_mask", emptyToNull(nameMask));
        p.addValue("msisdn_hash", emptyToNull(msisdnHash));
        p.addValue("msisdn_mask", emptyToNull(msisdnMask));
        p.addValue("status", status == null ? DEFAULT_STATUS : status);
        p.addValue("provider_reference", emptyToNull(providerReference));
        p.addValue("verified_at", verifiedAt == null ? null : Timestamp.from(verifiedAt));
        p.addValue("expires_at", expiresAt == null ? null : Timestamp.from(expiresAt));
        jdbcTemplate.update(
                "INSERT INTO verified_profiles "
                        + "(merchant_id, identity_number_hash, identity_number_mask, "
                        + "full_name_hash, full_name_mask, msisdn_hash, msisdn_mask, "
                        + "verification_status, verified_service, provider_reference, "
                        + "verified_at, expires_at) "
                        + "VALUES (:merchant_id, :nin_hash, :nin_mask, :name_hash, :name_mask, "
                        + ":msisdn_hash, :msisdn_mask, :status, 'gnugrid', :provider_reference, "
                        + ":verified_at, :expires_at) "
                        + "ON DUPLICATE KEY UPDATE merchant_id=:merchant_id, "
                        + "full_name_hash=:name_hash, full_name_mask=:name_mask, "
                        + "msisdn_hash=:msisdn_hash, msisdn_mask=:msisdn_mask, "
                        + "verification_status=:status, provider_reference=:provider_reference, "
                        + "verified_at=:verified_at, expires_at=:expires_at, "
                        + "updated_at=CURRENT_TIMESTAMP",
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

    private IdentityVerificationConnector findConnector(String providerCode) {
        return connectors.stream()
                .filter(c -> providerCode.equalsIgnoreCase(c.providerCode()))
                .findFirst()
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "No identity connector registered for " + providerCode));
    }

    private Map<String, Object> toRequestView(
            String reference,
            long merchantId,
            String nameMask,
            String msisdnMask,
            String ninHash,
            String ninMask,
            String status,
            String providerReference,
            String requestedBy) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("requestReference", reference);
        view.put("merchantId", merchantId);
        view.put("subjectNameMasked", nameMask);
        view.put("subjectMsisdnMasked", msisdnMask);
        view.put("identityNumberHash", ninHash);
        view.put("identityNumberMask", ninMask);
        view.put("status", status);
        view.put("providerReference", providerReference == null ? "" : providerReference);
        view.put("requestedBy", requestedBy == null ? "" : requestedBy);
        return view;
    }

    private String maskNin(String nin) {
        String digits = nin.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        if (digits.length() <= 4) {
            return "*".repeat(Math.max(1, digits.length()));
        }
        return digits.substring(0, 2)
                + "*".repeat(digits.length() - 4)
                + digits.substring(digits.length() - 2);
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
