package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settlement batch lifecycle with maker-checker separation (audit item: batch close was a
 * single-actor write through {@link #closeBatch}). {@link #closeBatch} is now the maker submit - it
 * moves the batch to {@code PENDING_APPROVAL}; a different actor must {@link #approveBatchClose}
 * (checker) to reach {@code CLOSED}.
 */
@Service
public class SettlementOpsService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DoubleEntryLedgerService ledgerService;

    public SettlementOpsService(
            NamedParameterJdbcTemplate jdbcTemplate, DoubleEntryLedgerService ledgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public void openBatch(
            String reference,
            String providerCode,
            String channelCode,
            String currency,
            BigDecimal expectedAmount,
            String openedBy) {
        String sql =
                "INSERT INTO reconciliation_settlement_batches "
                        + "(batch_reference, provider_code, channel_code, currency, expected_amount, opened_by) "
                        + "VALUES (:reference, :provider_code, :channel_code, :currency, :expected_amount, :opened_by) "
                        + "ON DUPLICATE KEY UPDATE expected_amount=VALUES(expected_amount)";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("provider_code", providerCode);
        p.addValue("channel_code", channelCode);
        p.addValue("currency", currency);
        p.addValue("expected_amount", expectedAmount);
        p.addValue("opened_by", openedBy);
        jdbcTemplate.update(sql, p);
        if (expectedAmount != null && expectedAmount.compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.post(
                    "settlement:" + reference,
                    "SETTLEMENT",
                    reference,
                    "Settlement sweep " + reference,
                    List.of(
                            new LedgerEntryCommand(
                                    "cpay:" + currency + ":settlement_clearing",
                                    "CPay settlement clearing",
                                    "SETTLEMENT_CLEARING",
                                    "SYSTEM",
                                    null,
                                    "DR",
                                    expectedAmount,
                                    currency,
                                    reference),
                            new LedgerEntryCommand(
                                    "provider:" + channelCode + ":" + currency + ":float",
                                    "Provider float",
                                    "PROVIDER_FLOAT",
                                    "PROVIDER",
                                    null,
                                    "CR",
                                    expectedAmount,
                                    currency,
                                    reference)));
        }
    }

    public int flagRecord(long recordId, String category, String batchReference) {
        String sql =
                "UPDATE reconciliation_records SET exception_category=:category, settlement_batch=:batch_reference WHERE id=:id";
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("id", recordId);
        p.addValue("category", category);
        p.addValue("batch_reference", batchReference);
        return jdbcTemplate.update(sql, p);
    }

    /**
     * Maker submit: moves the batch to {@code PENDING_APPROVAL}. The old single-step {@code CLOSED}
     * write is preserved for internal callers that explicitly pass a matching maker and checker
     * (see the note on {@link #approveBatchClose}).
     */
    public int closeBatch(String reference, String closedBy) {
        return requestBatchClose(reference, closedBy);
    }

    public int requestBatchClose(String reference, String requestedBy) {
        if (requestedBy == null || requestedBy.isBlank()) {
            requestedBy = "system";
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("requested_by", requestedBy.trim());
        return jdbcTemplate.update(
                "UPDATE reconciliation_settlement_batches SET batch_status='PENDING_APPROVAL', "
                        + "close_requested_by=:requested_by, close_requested_at=CURRENT_TIMESTAMP "
                        + "WHERE batch_reference=:reference AND batch_status<>'CLOSED'",
                p);
    }

    /**
     * Checker approval: the approver must differ from the requester, and the batch must still be
     * PENDING_APPROVAL. Returns the number of rows flipped to CLOSED (0 means the approver was the
     * maker, the batch was not pending, or it did not exist).
     */
    public int approveBatchClose(String reference, String approvedBy) {
        if (approvedBy == null || approvedBy.isBlank()) {
            approvedBy = "system";
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("approved_by", approvedBy.trim());
        return jdbcTemplate.update(
                "UPDATE reconciliation_settlement_batches SET batch_status='CLOSED', "
                        + "closed_by=:approved_by, closed_at=CURRENT_TIMESTAMP, "
                        + "close_approved_by=:approved_by, close_approved_at=CURRENT_TIMESTAMP "
                        + "WHERE batch_reference=:reference AND batch_status='PENDING_APPROVAL' "
                        + "AND close_requested_by IS NOT NULL AND close_requested_by<>:approved_by",
                p);
    }

    /**
     * Checker rejection: keeps the batch PENDING_APPROVAL (awaiting a corrected re-submit) and
     * records the reason.
     */
    public int rejectBatchClose(String reference, String rejectedBy, String reason) {
        if (rejectedBy == null || rejectedBy.isBlank()) {
            rejectedBy = "system";
        }
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", reference);
        p.addValue("rejected_by", rejectedBy.trim());
        p.addValue(
                "reason",
                reason == null || reason.isBlank() ? "Rejected by checker" : reason.trim());
        return jdbcTemplate.update(
                "UPDATE reconciliation_settlement_batches SET close_rejection_reason=:reason "
                        + "WHERE batch_reference=:reference AND batch_status='PENDING_APPROVAL' "
                        + "AND close_requested_by IS NOT NULL AND close_requested_by<>:rejected_by",
                p);
    }

    /**
     * Throws when a batch close approval could not be applied - callers decide whether 0 rows is an
     * error.
     */
    public void requireApproved(String reference, String approvedBy) {
        int updated = approveBatchClose(reference, approvedBy);
        if (updated == 0) {
            MapSqlParameterSource p = new MapSqlParameterSource("reference", reference);
            String status =
                    jdbcTemplate.queryForObject(
                            "SELECT batch_status FROM reconciliation_settlement_batches WHERE batch_reference=:reference",
                            p,
                            String.class);
            if (status == null) {
                throw new PaymentGatewayException("Settlement batch not found: " + reference);
            }
            if ("PENDING_APPROVAL".equals(status)) {
                throw new PaymentGatewayException(
                        "Settlement batch close requires a different approver than the requester");
            }
            throw new PaymentGatewayException(
                    "Settlement batch is not awaiting approval (status=" + status + ")");
        }
    }
}
