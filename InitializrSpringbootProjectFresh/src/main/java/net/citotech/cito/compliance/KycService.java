package net.citotech.cito.compliance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.security.CanonicalRequestSigner;
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
     * record to APPROVED or REJECTED. Only a record still in a screening status is updated, so a
     * re-review cannot overwrite a prior decision. The {@code reviewedBy} actor is accepted for API
     * symmetry with {@link #reviewDocument(long, String, String)}; the owner table has no reviewer
     * column, so it is intentionally not persisted.
     */
    @Transactional
    public int reviewOwner(long ownerId, String decision, String reviewedBy) {
        String status = normalizeDecision(decision);
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", ownerId);
        p.addValue("screening_status", status);
        return jdbcTemplate.update(
                "UPDATE beneficial_owners SET screening_status=:screening_status, updated_at=CURRENT_TIMESTAMP "
                        + "WHERE id=:id AND screening_status IN ('PENDING','IN_REVIEW')",
                p);
    }

    /**
     * Admin KYB review action for a KYC document: moves a pending record to APPROVED or REJECTED
     * and stamps the verifier. The UPDATE predicate also requires the record to still be PENDING,
     * so a re-review cannot overwrite a prior verification.
     */
    @Transactional
    public int reviewDocument(long documentId, String decision, String reviewedBy) {
        String status = normalizeDecision(decision);
        String reviewer = blank(reviewedBy) ? "system" : reviewedBy.trim();
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", documentId);
        p.addValue("verification_status", status);
        p.addValue("verified_by", reviewer);
        return jdbcTemplate.update(
                "UPDATE merchant_kyc_documents SET verification_status=:verification_status, "
                        + "verified_by=:verified_by, verified_at=CURRENT_TIMESTAMP "
                        + "WHERE id=:id AND verification_status IN ('PENDING','IN_REVIEW')",
                p);
    }

    private String normalizeDecision(String decision) {
        if (decision == null) {
            return "APPROVED";
        }
        String normalized = decision.trim().toUpperCase(Locale.ROOT);
        if ("REJECT".equals(normalized) || "REJECTED".equals(normalized)) {
            return "REJECTED";
        }
        return "APPROVED";
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
