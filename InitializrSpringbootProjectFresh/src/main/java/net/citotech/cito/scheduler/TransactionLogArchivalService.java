package net.citotech.cito.scheduler;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import net.citotech.cito.Common;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archival policy for {@code merchant_transactions_log} (audit F3): an append-heavy, unbounded
 * money-movement ledger table with no archival path, so query and backup cost only ever grows.
 *
 * This deliberately splits "archive" (copy old terminal rows into a disconnected archive table,
 * then mark them) from "purge" (physically delete the now-archived rows from the live table).
 * Deleting a row from {@code merchant_transactions_log} triggers merchant_statement's
 * {@code ON DELETE SET NULL} foreign key, silently severing old statement rows from their
 * transaction reference data (tx_merchant_ref/tx_unique_id/status shown in
 * MerchantStatementExportService's statement export) - a real, easy-to-miss regression for a
 * payments ledger. Archiving-without-purging is safe and reversible; purging is a separate,
 * explicitly-gated step so enabling it is a deliberate operational decision, not a side effect of
 * turning on archival.
 */
@Service
public class TransactionLogArchivalService {
    private static final List<String> TERMINAL_STATUSES = List.of("SUCCESSFUL", "FAILED");

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public TransactionLogArchivalService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Copies up to {@code batchSize} terminal, not-yet-archived rows older than {@code retentionDays}
     * into the archive table and marks them archived_on. Never touches PENDING/UNDETERMINED rows -
     * only a terminal transaction (see TransactionStatus.isTerminal) can never change again, so only
     * those are safe to copy out.
     */
    @Transactional
    public int archiveBatch(int retentionDays, int batchSize) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS));
        List<Long> ids = candidateIds(cutoff, batchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        MapSqlParameterSource p = new MapSqlParameterSource("ids", ids);
        jdbcTemplate.update(
            "INSERT IGNORE INTO merchant_transactions_log_archive "
                + "(id, merchant_id, gateway_id, original_amount, charges, status, charging_method, "
                + "tx_request_trace, tx_update_trace, tx_description, tx_merchant_description, tx_unique_id, "
                + "tx_gateway_ref, tx_merchant_ref, created_on, updated_on, payer_number, tx_type, "
                + "merchant_batch_transactions_log_id, tx_cost, callback_url, callback_trace, name, "
                + "account_type, beneficiary_id, originate_ip, resolved_by, safaricom_request_reference, "
                + "callback_status, callback_retry_count, callback_next_retry, currency, network_reference, "
                + "archived_on) "
                + "SELECT id, merchant_id, gateway_id, original_amount, charges, status, charging_method, "
                + "tx_request_trace, tx_update_trace, tx_description, tx_merchant_description, tx_unique_id, "
                + "tx_gateway_ref, tx_merchant_ref, created_on, updated_on, payer_number, tx_type, "
                + "merchant_batch_transactions_log_id, tx_cost, callback_url, callback_trace, name, "
                + "account_type, beneficiary_id, originate_ip, resolved_by, safaricom_request_reference, "
                + "callback_status, callback_retry_count, callback_next_retry, currency, network_reference, "
                + "CURRENT_TIMESTAMP "
                + "FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " WHERE id IN (:ids)",
            p);
        return jdbcTemplate.update(
            "UPDATE " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " SET archived_on=CURRENT_TIMESTAMP "
                + "WHERE id IN (:ids) AND archived_on IS NULL",
            p);
    }

    /**
     * Physically removes rows that have already been archived for at least {@code purgeAfterDays}.
     * Only ever touches rows with archived_on already set (i.e. a verified copy already exists in
     * the archive table) - never deletes a row that hasn't been archived first.
     */
    @Transactional
    public int purgeBatch(int purgeAfterDays, int batchSize) {
        Timestamp cutoff = Timestamp.from(Instant.now().minus(Math.max(1, purgeAfterDays), ChronoUnit.DAYS));
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("cutoff", cutoff);
        p.addValue("limit", Math.max(1, Math.min(batchSize, 5000)));
        List<Long> ids = jdbcTemplate.query(
            "SELECT id FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " "
                + "WHERE archived_on IS NOT NULL AND archived_on < :cutoff LIMIT :limit",
            p,
            (rs, rowNum) -> rs.getLong("id"));
        if (ids.isEmpty()) {
            return 0;
        }
        return jdbcTemplate.update(
            "DELETE FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " WHERE id IN (:ids)",
            new MapSqlParameterSource("ids", ids));
    }

    private List<Long> candidateIds(Timestamp cutoff, int batchSize) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("cutoff", cutoff);
        p.addValue("statuses", TERMINAL_STATUSES);
        p.addValue("limit", Math.max(1, Math.min(batchSize, 5000)));
        return jdbcTemplate.query(
            "SELECT id FROM " + Common.DB_TABLE_MERCHANT_TRANSACTION_LOG + " "
                + "WHERE archived_on IS NULL AND status IN (:statuses) AND created_on < :cutoff "
                + "ORDER BY id ASC LIMIT :limit",
            p,
            (rs, rowNum) -> rs.getLong("id"));
    }
}
