package net.citotech.cito;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.ledger.DoubleEntryLedgerService;
import net.citotech.cito.ledger.LedgerEntryCommand;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Automates settlement posting into auditable, balanced debit/credit entries. */
@Service
public class SettlementPostingAutomationService {

    private final JdbcTemplate jdbcTemplate;
    private final DoubleEntryLedgerService ledgerService;

    public SettlementPostingAutomationService(JdbcTemplate jdbcTemplate, DoubleEntryLedgerService ledgerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public Map<String, Object> postFinanceSettlement(Long settlementBatchId, String postedBy) {
        Map<String, Object> batch = fetchOne(
                "select id, settlement_reference, currency_code, net_amount, status "
                        + "from finance_settlement_batches where id = ?",
                settlementBatchId);
        requireApproved(value(batch, "status"), "finance settlement batch");
        rejectDuplicatePosting("FINANCE", settlementBatchId);

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "select id, transaction_reference, net_amount from finance_settlement_items where settlement_batch_id = ?",
                settlementBatchId);
        PostingTotals totals = totals(items, "net_amount", numeric(batch.get("net_amount")));
        String currency = value(batch, "currency_code");
        Long runId = createPostingRun(settlementBatchId, "FINANCE", currency, totals, postedBy);

        for (Map<String, Object> item : items) {
            BigDecimal amount = numeric(item.get("net_amount"));
            String reference = value(item, "transaction_reference");
            insertPostingEntry(runId, longValue(item.get("id")), "SETTLEMENT_CLEARING", "DEBIT", amount, currency, reference);
            insertPostingEntry(runId, longValue(item.get("id")), "MERCHANT_PAYABLE", "CREDIT", amount, currency, reference);
        }
        postLedger("settlement:finance:" + settlementBatchId, "FINANCE_SETTLEMENT", value(batch, "settlement_reference"),
                "Automated finance settlement posting", "SETTLEMENT_CLEARING", "MERCHANT_PAYABLE", totals.postedTotal(), currency);
        jdbcTemplate.update(
                "update finance_settlement_batches set status = 'PAID', paid_at = current_timestamp, updated_at = current_timestamp where id = ?",
                settlementBatchId);
        return result(runId, settlementBatchId, "FINANCE", totals);
    }

    @Transactional
    public Map<String, Object> postCorridorSettlement(Long corridorSettlementBatchId, String postedBy) {
        Map<String, Object> batch = fetchOne(
                "select id, settlement_reference, settlement_currency_code, net_amount, status "
                        + "from corridor_settlement_batches where id = ?",
                corridorSettlementBatchId);
        requireApproved(value(batch, "status"), "corridor settlement batch");
        rejectDuplicatePosting("CORRIDOR", corridorSettlementBatchId);

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "select id, transfer_id, settlement_amount from corridor_settlement_items where settlement_batch_id = ?",
                corridorSettlementBatchId);
        PostingTotals totals = totals(items, "settlement_amount", numeric(batch.get("net_amount")));
        String currency = value(batch, "settlement_currency_code");
        Long runId = createPostingRun(corridorSettlementBatchId, "CORRIDOR", currency, totals, postedBy);

        for (Map<String, Object> item : items) {
            BigDecimal amount = numeric(item.get("settlement_amount"));
            String reference = "TRANSFER-" + item.get("transfer_id");
            insertPostingEntry(runId, longValue(item.get("id")), "CORRIDOR_CLEARING", "DEBIT", amount, currency, reference);
            insertPostingEntry(runId, longValue(item.get("id")), "PARTNER_PAYABLE", "CREDIT", amount, currency, reference);
        }
        postLedger("settlement:corridor:" + corridorSettlementBatchId, "CORRIDOR_SETTLEMENT", value(batch, "settlement_reference"),
                "Automated corridor settlement posting", "CORRIDOR_CLEARING", "PARTNER_PAYABLE", totals.postedTotal(), currency);
        jdbcTemplate.update(
                "update corridor_settlement_batches set status = 'PAID', paid_at = current_timestamp, updated_at = current_timestamp where id = ?",
                corridorSettlementBatchId);
        return result(runId, corridorSettlementBatchId, "CORRIDOR", totals);
    }

    public List<Map<String, Object>> postingRuns(int limit) {
        return jdbcTemplate.queryForList(
                "select id, settlement_batch_id, settlement_batch_type, run_status, currency, expected_total, posted_total, "
                        + "variance_amount, entry_count, created_at, posted_at from settlement_posting_runs order by created_at desc limit ?",
                safeLimit(limit));
    }

    private Long createPostingRun(Long settlementBatchId, String type, String currency, PostingTotals totals, String postedBy) {
        jdbcTemplate.update(
                "insert into settlement_posting_runs "
                        + "(settlement_batch_id, settlement_batch_type, run_status, currency, expected_total, posted_total, "
                        + "entry_count, variance_amount, requested_by, posted_by, posted_at) "
                        + "values (?, ?, 'POSTED', ?, ?, ?, ?, ?, ?, ?, current_timestamp)",
                settlementBatchId,
                type,
                currency,
                totals.expectedTotal(),
                totals.postedTotal(),
                totals.entryCount(),
                totals.variance(),
                postedBy,
                postedBy);
        return lastInsertId();
    }

    private void insertPostingEntry(Long runId, Long itemId, String accountCode, String side, BigDecimal amount, String currency, String reference) {
        jdbcTemplate.update(
                "insert into settlement_posting_entries "
                        + "(posting_run_id, settlement_item_id, account_code, entry_side, amount, currency, reference, memo) "
                        + "values (?, ?, ?, ?, ?, ?, ?, ?)",
                runId,
                itemId,
                accountCode,
                side,
                amount,
                currency,
                reference,
                "Automated settlement posting");
    }

    private void postLedger(String transactionReference, String sourceType, String sourceReference, String description,
                            String debitAccount, String creditAccount, BigDecimal amount, String currency) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Settlement posting amount must be greater than zero");
        }
        ledgerService.post(
                transactionReference,
                sourceType,
                sourceReference,
                description,
                List.of(
                        new LedgerEntryCommand(debitAccount, debitAccount, "ASSET", "SYSTEM", null, "DR", amount, currency, description),
                        new LedgerEntryCommand(creditAccount, creditAccount, "LIABILITY", "SYSTEM", null, "CR", amount, currency, description)));
    }

    private PostingTotals totals(List<Map<String, Object>> items, String amountField, BigDecimal expectedTotal) {
        if (items.isEmpty()) {
            throw new IllegalStateException("Settlement batch has no items to post");
        }
        BigDecimal postedTotal = items.stream()
                .map(item -> numeric(item.get(amountField)))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal variance = postedTotal.subtract(expectedTotal);
        if (variance.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Settlement posting is not balanced against expected total; variance " + variance);
        }
        return new PostingTotals(expectedTotal, postedTotal, items.size() * 2, variance);
    }

    private void requireApproved(String status, String description) {
        if (!"APPROVED".equalsIgnoreCase(status)) {
            throw new IllegalStateException(description + " must be APPROVED before posting; found " + status);
        }
    }

    private void rejectDuplicatePosting(String type, Long settlementBatchId) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from settlement_posting_runs where settlement_batch_type = ? and settlement_batch_id = ?",
                Integer.class,
                type,
                settlementBatchId);
        if (count != null && count > 0) {
            throw new IllegalStateException("Settlement batch has already been posted");
        }
    }

    private Map<String, Object> result(Long runId, Long settlementBatchId, String type, PostingTotals totals) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postingRunId", runId);
        result.put("settlementBatchId", settlementBatchId);
        result.put("settlementType", type);
        result.put("postedTotal", totals.postedTotal());
        result.put("expectedTotal", totals.expectedTotal());
        result.put("varianceAmount", totals.variance());
        result.put("entryCount", totals.entryCount());
        result.put("status", "POSTED");
        return result;
    }

    private Map<String, Object> fetchOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Settlement record not found");
        }
        return rows.get(0);
    }

    private Long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return id == null ? 0L : id;
    }

    private static BigDecimal numeric(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private static int safeLimit(int limit) {
        return Math.max(1, Math.min(limit, 250));
    }

    private static String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }

    private record PostingTotals(BigDecimal expectedTotal, BigDecimal postedTotal, int entryCount, BigDecimal variance) {}
}
