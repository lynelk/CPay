package net.citotech.cito.refund;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentDisputeService {
    private static final Set<String> DISPUTE_TYPES =
            Set.of("CUSTOMER_CLAIM", "DUPLICATE", "SERVICE_NOT_RECEIVED", "FRAUD", "OTHER");
    private static final Set<String> DISPUTE_STATUSES =
            Set.of("OPEN", "UNDER_REVIEW", "AWAITING_EVIDENCE", "RESOLVED", "REJECTED", "CLOSED");
    private static final Set<String> REVERSAL_TYPES =
            Set.of("PROVIDER_REVERSAL", "OPERATOR_REVERSAL", "SYSTEM_REVERSAL", "MANUAL_REVERSAL");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public PaymentDisputeService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> openDispute(
            long merchantId,
            String transactionReference,
            String disputeType,
            BigDecimal amount,
            String currencyCode,
            String reasonCode,
            String customerReference,
            String actor,
            Instant dueAt) {
        requireMerchant(merchantId);
        String type = normalize(disputeType, DISPUTE_TYPES, "disputeType");
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("amount must be greater than zero");
        }
        String reference =
                "DSP-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO payment_disputes "
                        + "(dispute_reference, merchant_id, transaction_reference, dispute_type, amount, currency_code, status, "
                        + "reason_code, customer_reference, due_at, opened_by) "
                        + "VALUES (:reference, :merchant_id, :transaction_reference, :dispute_type, :amount, :currency_code, 'OPEN', "
                        + ":reason_code, :customer_reference, :due_at, :opened_by)",
                new MapSqlParameterSource()
                        .addValue("reference", reference)
                        .addValue("merchant_id", merchantId)
                        .addValue(
                                "transaction_reference",
                                required(transactionReference, "transactionReference"))
                        .addValue("dispute_type", type)
                        .addValue("amount", amount)
                        .addValue("currency_code", blankToNull(upper(currencyCode)))
                        .addValue("reason_code", blankToNull(reasonCode))
                        .addValue("customer_reference", blankToNull(customerReference))
                        .addValue("due_at", dueAt == null ? null : Timestamp.from(dueAt))
                        .addValue("opened_by", blankToNull(actor)));
        addEvent(reference, "DISPUTE_OPENED", actor, null, null);
        return dispute(merchantId, reference);
    }

    @Transactional
    public Map<String, Object> addEvent(
            String disputeReference,
            String eventType,
            String actor,
            String notes,
            String evidenceReference) {
        long disputeId = disputeId(disputeReference);
        jdbcTemplate.update(
                "INSERT INTO payment_dispute_events "
                        + "(dispute_id, event_type, actor_reference, notes, evidence_reference) "
                        + "VALUES (:dispute_id, :event_type, :actor_reference, :notes, :evidence_reference)",
                new MapSqlParameterSource()
                        .addValue("dispute_id", disputeId)
                        .addValue("event_type", required(eventType, "eventType").toUpperCase(Locale.ROOT))
                        .addValue("actor_reference", blankToNull(actor))
                        .addValue("notes", blankToNull(notes))
                        .addValue("evidence_reference", blankToNull(evidenceReference)));
        return Map.of("disputeReference", disputeReference, "eventRecorded", true);
    }

    @Transactional
    public Map<String, Object> updateStatus(
            long merchantId,
            String disputeReference,
            String status,
            String actor,
            String notes) {
        String normalized = normalize(status, DISPUTE_STATUSES, "status");
        int updated =
                jdbcTemplate.update(
                        "UPDATE payment_disputes SET status=:status, updated_at=CURRENT_TIMESTAMP, "
                                + "closed_at=CASE WHEN :status IN ('RESOLVED','REJECTED','CLOSED') THEN CURRENT_TIMESTAMP ELSE closed_at END "
                                + "WHERE merchant_id=:merchant_id AND dispute_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(disputeReference, "disputeReference"))
                                .addValue("status", normalized));
        if (updated == 0) {
            throw new PaymentGatewayException("Dispute was not found");
        }
        addEvent(disputeReference, "STATUS_" + normalized, actor, notes, null);
        return dispute(merchantId, disputeReference);
    }

    public List<Map<String, Object>> disputes(long merchantId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbcTemplate.queryForList(
                "SELECT dispute_reference AS disputeReference, transaction_reference AS transactionReference, "
                        + "dispute_type AS disputeType, amount, currency_code AS currencyCode, status, reason_code AS reasonCode, "
                        + "customer_reference AS customerReference, assigned_to AS assignedTo, due_at AS dueAt, "
                        + "opened_by AS openedBy, created_at AS createdAt, updated_at AS updatedAt, closed_at AS closedAt "
                        + "FROM payment_disputes WHERE merchant_id=:merchant_id ORDER BY id DESC LIMIT "
                        + safeLimit,
                new MapSqlParameterSource("merchant_id", merchantId));
    }

    public List<Map<String, Object>> events(long merchantId, String disputeReference) {
        Map<String, Object> dispute = dispute(merchantId, disputeReference);
        long id = ((Number) dispute.get("id")).longValue();
        return jdbcTemplate.queryForList(
                "SELECT event_type AS eventType, actor_reference AS actorReference, notes, "
                        + "evidence_reference AS evidenceReference, created_at AS createdAt "
                        + "FROM payment_dispute_events WHERE dispute_id=:dispute_id ORDER BY id",
                new MapSqlParameterSource("dispute_id", id));
    }

    @Transactional
    public Map<String, Object> recordReversal(
            long merchantId,
            long originalTransactionId,
            String originalMerchantRef,
            String providerChannel,
            String providerReference,
            BigDecimal amount,
            String currencyCode,
            String reversalType,
            String reasonCode,
            String evidenceJson) {
        requireMerchant(merchantId);
        if (originalTransactionId <= 0) {
            throw new PaymentGatewayException("originalTransactionId must be positive");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentGatewayException("amount must be greater than zero");
        }
        String type = normalize(reversalType, REVERSAL_TYPES, "reversalType");
        String reference =
                "REV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        jdbcTemplate.update(
                "INSERT INTO payment_reversals "
                        + "(reversal_reference, merchant_id, original_transaction_id, original_merchant_ref, provider_channel, "
                        + "provider_reference, amount, currency_code, reversal_type, status, reason_code, evidence_json) "
                        + "VALUES (:reference, :merchant_id, :original_transaction_id, :original_merchant_ref, :provider_channel, "
                        + ":provider_reference, :amount, :currency_code, :reversal_type, 'RECEIVED', :reason_code, :evidence_json)",
                new MapSqlParameterSource()
                        .addValue("reference", reference)
                        .addValue("merchant_id", merchantId)
                        .addValue("original_transaction_id", originalTransactionId)
                        .addValue(
                                "original_merchant_ref",
                                required(originalMerchantRef, "originalMerchantRef"))
                        .addValue("provider_channel", blankToNull(providerChannel))
                        .addValue("provider_reference", blankToNull(providerReference))
                        .addValue("amount", amount)
                        .addValue("currency_code", required(currencyCode, "currencyCode").toUpperCase(Locale.ROOT))
                        .addValue("reversal_type", type)
                        .addValue("reason_code", blankToNull(reasonCode))
                        .addValue("evidence_json", blankToNull(evidenceJson)));
        return jdbcTemplate.queryForMap(
                "SELECT reversal_reference AS reversalReference, original_merchant_ref AS originalMerchantRef, "
                        + "provider_channel AS providerChannel, provider_reference AS providerReference, amount, currency_code AS currencyCode, "
                        + "reversal_type AS reversalType, status, reason_code AS reasonCode, created_at AS createdAt "
                        + "FROM payment_reversals WHERE reversal_reference=:reference",
                new MapSqlParameterSource("reference", reference));
    }

    private Map<String, Object> dispute(long merchantId, String reference) {
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT id, dispute_reference AS disputeReference, transaction_reference AS transactionReference, "
                                + "dispute_type AS disputeType, amount, currency_code AS currencyCode, status, reason_code AS reasonCode, "
                                + "customer_reference AS customerReference, assigned_to AS assignedTo, due_at AS dueAt, "
                                + "opened_by AS openedBy, created_at AS createdAt, updated_at AS updatedAt, closed_at AS closedAt "
                                + "FROM payment_disputes WHERE merchant_id=:merchant_id AND dispute_reference=:reference",
                        new MapSqlParameterSource()
                                .addValue("merchant_id", merchantId)
                                .addValue("reference", required(reference, "disputeReference")));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Dispute was not found");
        }
        return rows.get(0);
    }

    private long disputeId(String reference) {
        List<Long> rows =
                jdbcTemplate.query(
                        "SELECT id FROM payment_disputes WHERE dispute_reference=:reference",
                        new MapSqlParameterSource("reference", required(reference, "disputeReference")),
                        (rs, rowNum) -> rs.getLong("id"));
        if (rows.isEmpty()) {
            throw new PaymentGatewayException("Dispute was not found");
        }
        return rows.get(0);
    }

    private String normalize(String value, Set<String> allowed, String field) {
        String normalized = required(value, field).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new PaymentGatewayException("Unsupported " + field);
        }
        return normalized;
    }

    private void requireMerchant(long merchantId) {
        if (merchantId <= 0) {
            throw new PaymentGatewayException("merchantId must be positive");
        }
    }

    private String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private String upper(String value) {
        return value == null ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}