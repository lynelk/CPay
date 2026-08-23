package net.citotech.cito.communication;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.Common;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Merchant-facing enqueue/status service over the durable communication_messages + outbox model.
 * Provider selection, charging, retry, failover and provider-health handling remain inside CPay.
 */
@Service
public class MerchantCommunicationService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MerchantCommunicationService(
            NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> enqueueSms(
            long merchantId,
            String recipient,
            String content,
            String purpose,
            String externalReference,
            String idempotencyKey,
            Integer expiresInSeconds) {
        if (merchantId <= 0) throw new IllegalArgumentException("merchantId is required.");
        if (blank(recipient)) throw new IllegalArgumentException("recipient is required.");
        if (blank(content)) throw new IllegalArgumentException("content is required.");
        if (content.length() > 1600) throw new IllegalArgumentException("SMS content is too long.");

        String normalizedPurpose = blank(purpose) ? "TRANSACTIONAL" : purpose.trim().toUpperCase();
        if (!List.of("TRANSACTIONAL", "OTP", "SECURITY", "NOTIFICATION").contains(normalizedPurpose)) {
            throw new IllegalArgumentException("Unsupported communication purpose.");
        }
        String normalizedExternal = trimToNull(externalReference, 128);
        String normalizedIdempotency = trimToNull(idempotencyKey, 128);
        if (normalizedIdempotency == null) normalizedIdempotency = normalizedExternal;

        if (normalizedIdempotency != null) {
            Map<String, Object> existing = findByIdempotency(merchantId, normalizedIdempotency);
            if (existing != null) return existing;
        }

        String publicId = "COM-" + Common.randomUrlSafeToken(18);
        Instant now = Instant.now();
        int ttl = expiresInSeconds == null ? 600 : Math.max(60, Math.min(86400, expiresInSeconds));
        String metadataJson = metadata(content);
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("public_id", publicId)
                .addValue("merchant_id", merchantId)
                .addValue("external_reference", normalizedExternal)
                .addValue("idempotency_key", normalizedIdempotency)
                .addValue("purpose", normalizedPurpose)
                .addValue("recipient", recipient.trim())
                .addValue("expires_at", Timestamp.from(now.plusSeconds(ttl)))
                .addValue("metadata_json", metadataJson);
        try {
            jdbcTemplate.update(
                    "INSERT INTO communication_messages "
                            + "(public_id, merchant_id, external_reference, idempotency_key, purpose, "
                            + "recipient_type, recipient, requested_channels, selected_channel, "
                            + "selected_provider_code, template_key, fallback_enabled, status, "
                            + "scheduled_at, expires_at, metadata_json) VALUES "
                            + "(:public_id, :merchant_id, :external_reference, :idempotency_key, :purpose, "
                            + "'PHONE', :recipient, 'SMS', 'SMS', NULL, NULL, 'N', 'RECEIVED', "
                            + "CURRENT_TIMESTAMP, :expires_at, :metadata_json)",
                    p);
        } catch (DuplicateKeyException duplicate) {
            if (normalizedIdempotency != null) {
                Map<String, Object> existing = findByIdempotency(merchantId, normalizedIdempotency);
                if (existing != null) return existing;
            }
            throw duplicate;
        }

        Long communicationId = jdbcTemplate.queryForObject(
                "SELECT id FROM communication_messages WHERE public_id=:public_id AND merchant_id=:merchant_id",
                p,
                Long.class);
        if (communicationId == null) throw new IllegalStateException("Communication could not be persisted.");
        jdbcTemplate.update(
                "INSERT INTO communication_outbox "
                        + "(communication_id, event_type, status, priority, attempts, next_attempt_at) "
                        + "VALUES (:communication_id, 'DISPATCH', 'PENDING', 'HIGH', 0, CURRENT_TIMESTAMP)",
                new MapSqlParameterSource("communication_id", communicationId));
        return findByPublicId(merchantId, publicId);
    }

    public Map<String, Object> status(long merchantId, String publicId) {
        if (merchantId <= 0 || blank(publicId)) return null;
        return findByPublicId(merchantId, publicId.trim());
    }

    private Map<String, Object> findByIdempotency(long merchantId, String key) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT public_id, external_reference, purpose, selected_channel, "
                        + "selected_provider_code, status, created_at, updated_at "
                        + "FROM communication_messages WHERE merchant_id=:merchant_id "
                        + "AND idempotency_key=:idempotency_key LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("idempotency_key", key));
        return rows.isEmpty() ? null : view(rows.get(0));
    }

    private Map<String, Object> findByPublicId(long merchantId, String publicId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT public_id, external_reference, purpose, selected_channel, "
                        + "selected_provider_code, status, created_at, updated_at "
                        + "FROM communication_messages WHERE merchant_id=:merchant_id "
                        + "AND public_id=:public_id LIMIT 1",
                new MapSqlParameterSource()
                        .addValue("merchant_id", merchantId)
                        .addValue("public_id", publicId));
        return rows.isEmpty() ? null : view(rows.get(0));
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("messageReference", row.get("public_id"));
        view.put("externalReference", row.get("external_reference"));
        view.put("purpose", row.get("purpose"));
        view.put("channel", row.get("selected_channel"));
        view.put("provider", row.get("selected_provider_code"));
        view.put("status", row.get("status"));
        view.put("createdAt", row.get("created_at"));
        view.put("updatedAt", row.get("updated_at"));
        return view;
    }

    private String metadata(String body) {
        try {
            return objectMapper.writeValueAsString(Map.of("body", body));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Communication content could not be encoded.");
        }
    }

    private String trimToNull(String value, int maxLength) {
        if (blank(value)) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("Reference is too long.");
        }
        return trimmed;
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
