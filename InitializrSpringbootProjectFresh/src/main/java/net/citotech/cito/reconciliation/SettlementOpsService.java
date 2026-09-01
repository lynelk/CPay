package net.citotech.cito.reconciliation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import net.citotech.cito.gateway.PaymentGatewayException;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import net.citotech.cito.money.MoneyAmount;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Settlement batch lifecycle with immutable commercial attributes and maker-checker close. */
@Service
public class SettlementOpsService {
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final DoubleEntryLedgerService ledgerService;

    public SettlementOpsService(
            NamedParameterJdbcTemplate jdbcTemplate, DoubleEntryLedgerService ledgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
    }

    /**
     * Opens a settlement batch exactly once. Replaying the same reference is idempotent only when
     * provider, channel, currency and amount are identical. Any conflicting replay fails closed so
     * the operational settlement record can never diverge from its idempotent ledger posting.
     */
    @Transactional
    public void openBatch(
            String reference,
            String providerCode,
            String channelCode,
            String currency,
            BigDecimal expectedAmount,
            String openedBy) {
        String normalizedReference = required(reference, "Settlement batch reference");
        String normalizedProvider = required(providerCode, "Settlement provider").toUpperCase();
        String normalizedChannel = required(channelCode, "Settlement channel").toUpperCase();
        String normalizedCurrency = required(currency, "Settlement currency").toUpperCase();
        String normalizedActor = required(openedBy, "Settlement opener");
        if (expectedAmount == null || expectedAmount.signum() < 0) {
            throw new PaymentGatewayException("Settlement expected amount must be non-negative");
        }
        BigDecimal normalizedAmount = MoneyAmount.normalize(expectedAmount);

        ExistingBatch existing = findForUpdate(normalizedReference);
        if (existing == null) {
            MapSqlParameterSource p = new MapSqlParameterSource();
            p.addValue("reference", normalizedReference);
            p.addValue("provider_code", normalizedProvider);
            p.addValue("channel_code", normalizedChannel);
            p.addValue("currency", normalizedCurrency);
            p.addValue("expected_amount", normalizedAmount);
            p.addValue("opened_by", normalizedActor);
            jdbcTemplate.update(
                    "INSERT INTO reconciliation_settlement_batches "
                            + "(batch_reference, provider_code, channel_code, currency, expected_amount, opened_by) "
                            + "VALUES (:reference, :provider_code, :channel_code, :currency, :expected_amount, :opened_by)",
                    p);
        } else if (!existing.matches(
                normalizedProvider, normalizedChannel, normalizedCurrency, normalizedAmount)) {
            throw new PaymentGatewayException(
                    "Settlement batch reference already exists with different commercial attributes: "
                            + normalizedReference);
        }

        if (normalizedAmount.signum() > 0) {
            ledgerService.post(
                    "settlement:" + normalizedReference,
                    "SETTLEMENT",
                    normalizedReference,
                    "Settlement sweep " + normalizedReference,
                    List.of(
                            new LedgerEntryCommand(
                                    "cpay:" + normalizedCurrency + ":settlement_clearing",
                                    "CPay settlement clearing",
                                    "SETTLEMENT_CLEARING",
                                    "SYSTEM",
                                    null,
                                    "DR",
                                    normalizedAmount,
                                    normalizedCurrency,
                                    normalizedReference),
                            new LedgerEntryCommand(
                                    "provider:"
                                            + normalizedChannel
                                            + ":"
                                            + normalizedCurrency
                                            + ":float",
                                    "Provider float",
                                    "PROVIDER_FLOAT",
                                    "PROVIDER",
                                    null,
                                    "CR",
                                    normalizedAmount,
                                    normalizedCurrency,
                                    normalizedReference)));
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

    public void requireApproved(String reference, String approvedBy) {
        int updated = approveBatchClose(reference, approvedBy);
        if (updated == 0) {
            MapSqlParameterSource p = new MapSqlParameterSource("reference", reference);
            List<String> statuses =
                    jdbcTemplate.query(
                            "SELECT batch_status FROM reconciliation_settlement_batches WHERE batch_reference=:reference",
                            p,
                            (rs, rowNum) -> rs.getString(1));
            if (statuses.isEmpty()) {
                throw new PaymentGatewayException("Settlement batch not found: " + reference);
            }
            String status = statuses.get(0);
            if ("PENDING_APPROVAL".equals(status)) {
                throw new PaymentGatewayException(
                        "Settlement batch close requires a different approver than the requester");
            }
            throw new PaymentGatewayException(
                    "Settlement batch is not awaiting approval (status=" + status + ")");
        }
    }

    private ExistingBatch findForUpdate(String reference) {
        List<ExistingBatch> batches =
                jdbcTemplate.query(
                        "SELECT provider_code, channel_code, currency, expected_amount "
                                + "FROM reconciliation_settlement_batches "
                                + "WHERE batch_reference=:reference FOR UPDATE",
                        new MapSqlParameterSource("reference", reference),
                        (rs, rowNum) ->
                                new ExistingBatch(
                                        rs.getString("provider_code"),
                                        rs.getString("channel_code"),
                                        rs.getString("currency"),
                                        MoneyAmount.normalize(
                                                rs.getBigDecimal("expected_amount"))));
        return batches.isEmpty() ? null : batches.get(0);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new PaymentGatewayException(field + " is required");
        }
        return value.trim();
    }

    private record ExistingBatch(
            String providerCode, String channelCode, String currency, BigDecimal expectedAmount) {
        private boolean matches(String provider, String channel, String ccy, BigDecimal expected) {
            return Objects.equals(normalize(providerCode), normalize(provider))
                    && Objects.equals(normalize(channelCode), normalize(channel))
                    && Objects.equals(normalize(currency), normalize(ccy))
                    && expectedAmount.compareTo(expected) == 0;
        }

        private static String normalize(String value) {
            return value == null ? null : value.trim().toUpperCase();
        }
    }
}
