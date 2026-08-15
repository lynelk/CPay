package net.citotech.cito.compliance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.CanonicalRequestSigner;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public KycService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long addBeneficialOwner(
            long merchantId,
            String fullName,
            String idType,
            String idValue,
            BigDecimal ownershipPercent) {
        if (merchantId <= 0 || blank(fullName)) {
            throw new PaymentGatewayException("merchantId and fullName are required");
        }
        validateOwnershipPercent(ownershipPercent);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("full_name", fullName.trim());
        p.addValue("id_type", blank(idType) ? null : idType.trim().toUpperCase(Locale.ROOT));
        p.addValue(
                "id_hash",
                blank(idValue)
                        ? null
                        : CanonicalRequestSigner.sha256Hex(
                                idValue.trim().toUpperCase(Locale.ROOT)));
        p.addValue("ownership_percent", ownershipPercent);
        jdbcTemplate.update(
                "INSERT INTO beneficial_owners "
                        + "(merchant_id, full_name, id_type, id_value_hash, ownership_percent, screening_status) "
                        + "VALUES (:merchant_id, :full_name, :id_type, :id_hash, :ownership_percent, 'PENDING')",
                p);
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    @Transactional
    public long addDocument(
            long merchantId, String documentType, String storageRef, String documentHash) {
        if (merchantId <= 0 || blank(documentType) || blank(storageRef)) {
            throw new PaymentGatewayException(
                    "merchantId, documentType, and storageRef are required");
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("merchant_id", merchantId);
        p.addValue("document_type", documentType.trim().toUpperCase(Locale.ROOT));
        p.addValue("storage_ref", storageRef.trim());
        p.addValue("document_hash", blank(documentHash) ? null : documentHash.trim());
        jdbcTemplate.update(
                "INSERT INTO merchant_kyc_documents "
                        + "(merchant_id, document_type, storage_ref, document_hash, verification_status) "
                        + "VALUES (:merchant_id, :document_type, :storage_ref, :document_hash, 'PENDING')",
                p);
        Long id =
                jdbcTemplate.queryForObject(
                        "SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    public List<Map<String, Object>> merchantKycSummary(long merchantId) {
        MapSqlParameterSource p = new MapSqlParameterSource("merchant_id", merchantId);
        return jdbcTemplate.queryForList(
                "SELECT 'OWNER' AS record_type, id, full_name AS label, screening_status AS status, created_at "
                        + "FROM beneficial_owners WHERE merchant_id=:merchant_id "
                        + "UNION ALL "
                        + "SELECT 'DOCUMENT' AS record_type, id, document_type AS label, verification_status AS status, created_at "
                        + "FROM merchant_kyc_documents WHERE merchant_id=:merchant_id "
                        + "ORDER BY created_at DESC",
                p);
    }

    /**
     * Admin KYB review action for a beneficial owner (audit P7/KYB workbench): moves a pending
     * record to APPROVED or REJECTED. Unsupported decisions fail closed and every successful
     * decision records reviewer attribution in {@code kyb_review_decisions}.
     */
    @Transactional
    public int reviewOwner(long ownerId, String decision, String reviewedBy) {
        return reviewOwner(ownerId, decision, reviewedBy, null, null);
    }

    @Transactional
    public int reviewOwner(
            long ownerId, String decision, String reviewedBy, String reviewerRole, String reason) {
        String status = normalizeDecision(decision);
        String reviewer = normalizeReviewer(reviewedBy);
        String previousStatus =
                lookupStatus(
                        "SELECT screening_status FROM beneficial_owners WHERE id=:id", ownerId);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", ownerId);
        p.addValue("screening_status", status);
        int updated =
                jdbcTemplate.update(
                        "UPDATE beneficial_owners SET screening_status=:screening_status, updated_at=CURRENT_TIMESTAMP "
                                + "WHERE id=:id AND screening_status IN ('PENDING','IN_REVIEW')",
                        p);
        if (updated > 0) {
            recordReviewDecision(
                    "BENEFICIAL_OWNER",
                    ownerId,
                    previousStatus,
                    status,
                    status,
                    reason,
                    reviewer,
                    reviewerRole);
        }
        return updated;
    }

    /**
     * Admin KYB review action for a KYC document: moves a pending record to APPROVED or REJECTED
     * and stamps the verifier. Unsupported decisions fail closed, and successful decisions are
     * recorded as compliance evidence.
     */
    @Transactional
    public int reviewDocument(long documentId, String decision, String reviewedBy) {
        return reviewDocument(documentId, decision, reviewedBy, null, null);
    }

    @Transactional
    public int reviewDocument(
            long documentId,
            String decision,
            String reviewedBy,
            String reviewerRole,
            String reason) {
        String status = normalizeDecision(decision);
        String reviewer = normalizeReviewer(reviewedBy);
        String previousStatus =
                lookupStatus(
                        "SELECT verification_status FROM merchant_kyc_documents WHERE id=:id",
                        documentId);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", documentId);
        p.addValue("verification_status", status);
        p.addValue("verified_by", reviewer);
        int updated =
                jdbcTemplate.update(
                        "UPDATE merchant_kyc_documents SET verification_status=:verification_status, "
                                + "verified_by=:verified_by, verified_at=CURRENT_TIMESTAMP "
                                + "WHERE id=:id AND verification_status IN ('PENDING','IN_REVIEW')",
                        p);
        if (updated > 0) {
            recordReviewDecision(
                    "KYC_DOCUMENT",
                    documentId,
                    previousStatus,
                    status,
                    status,
                    reason,
                    reviewer,
                    reviewerRole);
        }
        return updated;
    }

    private void validateOwnershipPercent(BigDecimal ownershipPercent) {
        if (ownershipPercent == null) {
            return;
        }
        if (ownershipPercent.compareTo(BigDecimal.ZERO) <= 0
                || ownershipPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new PaymentGatewayException(
                    "ownershipPercent must be greater than 0 and at most 100");
        }
    }

    private String normalizeDecision(String decision) {
        if (blank(decision)) {
            throw new PaymentGatewayException("KYC review decision is required");
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if ("APPROVE".equals(normalized) || "APPROVED".equals(normalized)) {
            return "APPROVED";
        }
        if ("REJECT".equals(normalized) || "REJECTED".equals(normalized)) {
            return "REJECTED";
        }
        throw new PaymentGatewayException("Unsupported KYC review decision: " + decision.trim());
    }

    private String normalizeReviewer(String reviewedBy) {
        if (blank(reviewedBy)) {
            throw new PaymentGatewayException("reviewedBy is required");
        }
        return reviewedBy.trim();
    }

    private String lookupStatus(String sql, long id) {
        try {
            return jdbcTemplate.queryForObject(
                    sql, new MapSqlParameterSource("id", id), String.class);
        } catch (EmptyResultDataAccessException ignored) {
            return null;
        }
    }

    private void recordReviewDecision(
            String subjectType,
            long subjectId,
            String oldStatus,
            String newStatus,
            String decision,
            String reason,
            String reviewedBy,
            String reviewerRole) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("subject_type", subjectType);
        p.addValue("subject_id", subjectId);
        p.addValue("old_status", oldStatus);
        p.addValue("new_status", newStatus);
        p.addValue("decision", decision);
        p.addValue("reason", blank(reason) ? null : reason.trim());
        p.addValue("reviewer_user_id", reviewedBy);
        p.addValue("reviewer_role", blank(reviewerRole) ? null : reviewerRole.trim());
        jdbcTemplate.update(
                "INSERT INTO kyb_review_decisions "
                        + "(subject_type, subject_id, old_status, new_status, decision, reason, reviewer_user_id, reviewer_role) "
                        + "VALUES (:subject_type, :subject_id, :old_status, :new_status, :decision, :reason, :reviewer_user_id, :reviewer_role)",
                p);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
