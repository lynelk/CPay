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
    public long addBeneficialOwner(long merchantId,
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
        p.addValue("id_hash", blank(idValue) ? null : CanonicalRequestSigner.sha256Hex(idValue.trim().toUpperCase(Locale.ROOT)));
        p.addValue("ownership_percent", ownershipPercent);
        jdbcTemplate.update(
            "INSERT INTO beneficial_owners "
                + "(merchant_id, full_name, id_type, id_value_hash, ownership_percent, screening_status) "
                + "VALUES (:merchant_id, :full_name, :id_type, :id_hash, :ownership_percent, 'PENDING')",
            p);
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    @Transactional
    public long addDocument(long merchantId,
                            String documentType,
                            String storageRef,
                            String documentHash) {
        if (merchantId <= 0 || blank(documentType) || blank(storageRef)) {
            throw new PaymentGatewayException("merchantId, documentType, and storageRef are required");
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
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
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

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
