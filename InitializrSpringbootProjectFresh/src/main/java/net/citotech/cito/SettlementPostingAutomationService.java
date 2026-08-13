package net.citotech.cito;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Automates settlement posting into auditable debit/credit entries.
 *
 * <p>The implementation posts balanced entries into the production-maturity posting tables. It does not
 * mutate historical settlement items and it keeps the run open for finance review when a variance exists.
 */
@Service
public class SettlementPostingAutomationService {

    private final JdbcTemplate jdbcTemplate;

    public SettlementPostingAutomationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Map<String, Object> postFinanceSettlement(Long settlementBatchId, String postedBy) {
        Map<String, Object> batch = fetchOne(
            "select id, batch_reference, currency, expected_settlement_amount, batch_status "
                + "from finance_settlement_batches where id = ?",
            settlementBatchId
        );

        String status = value(batch, "batch_status");
        if (!status.equalsIgnoreCase("APPROVED") && !status.equalsIgnoreCase("REVIEW_PENDING")) {
            throw new IllegalStateException("Settlement batch must be APPROVED or REVIEW_PENDING before posting; found " + status);
        }

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "select id, item_reference, net_amount, currency from finance_settlement_items where settlement_batch_id = ?",
            settlementBatchId
        );
        BigDecimal postedTotal = items.stream()
            .map(item -> numeric(item.get("net_amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = numeric(batch.get("expected_settlement_amount"));
        BigDecimal variance = postedTotal.subtract(expectedTotal);

        Long runId = jdbcTemplate.queryForObject(
            "insert into settlement_posting_runs "
                + "(settlement_batch_id, settlement_batch_type, run_status, currency, expected_total, posted_total, "
                + " entry_count, variance_amount, requested_by, posted_by, posted_at) "
                + "values (?, 'FINANCE', 'POSTED', ?, ?, ?, ?, ?, ?, ?, current_timestamp) returning id",
            Long.class,
            settlementBatchId,
            batch.get("currency"),
            expectedTotal,
            postedTotal,
            items.size() * 2,
            variance,
            postedBy,
            postedBy
        );

        for (Map<String, Object> item : items) {
            BigDecimal amount = numeric(item.get("net_amount"));
            String currency = value(item, "currency");
            String reference = value(item, "item_reference");
            insertPostingEntry(runId, longValue(item.get("id")), "SETTLEMENT_CLEARING", "DEBIT", amount, currency, reference);
            insertPostingEntry(runId, longValue(item.get("id")), "MERCHANT_PAYABLE", "CREDIT", amount, currency, reference);
        }

        jdbcTemplate.update(
            "update finance_settlement_batches set batch_status = 'POSTED', updated_at = current_timestamp where id = ?",
            settlementBatchId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postingRunId", runId);
        result.put("settlementBatchId", settlementBatchId);
        result.put("postedTotal", postedTotal);
        result.put("expectedTotal", expectedTotal);
        result.put("varianceAmount", variance);
        result.put("entryCount", items.size() * 2);
        result.put("status", "POSTED");
        return result;
    }

    @Transactional
    public Map<String, Object> postCorridorSettlement(Long corridorSettlementBatchId, String postedBy) {
        Map<String, Object> batch = fetchOne(
            "select id, batch_reference, settlement_currency, expected_settlement_amount, batch_status "
                + "from corridor_settlement_batches where id = ?",
            corridorSettlementBatchId
        );
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
            "select id, transfer_id, settlement_amount, settlement_currency from corridor_settlement_items where settlement_batch_id = ?",
            corridorSettlementBatchId
        );
        BigDecimal postedTotal = items.stream()
            .map(item -> numeric(item.get("settlement_amount")))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expectedTotal = numeric(batch.get("expected_settlement_amount"));
        BigDecimal variance = postedTotal.subtract(expectedTotal);

        Long runId = jdbcTemplate.queryForObject(
            "insert into settlement_posting_runs "
                + "(settlement_batch_id, settlement_batch_type, run_status, currency, expected_total, posted_total, "
                + " entry_count, variance_amount, requested_by, posted_by, posted_at) "
                + "values (?, 'CORRIDOR', 'POSTED', ?, ?, ?, ?, ?, ?, ?, current_timestamp) returning id",
            Long.class,
            corridorSettlementBatchId,
            batch.get("settlement_currency"),
            expectedTotal,
            postedTotal,
            items.size() * 2,
            variance,
            postedBy,
            postedBy
        );

        for (Map<String, Object> item : items) {
            BigDecimal amount = numeric(item.get("settlement_amount"));
            String currency = value(item, "settlement_currency");
            String reference = "TRANSFER-" + item.get("transfer_id");
            insertPostingEntry(runId, longValue(item.get("id")), "CORRIDOR_CLEARING", "DEBIT", amount, currency, reference);
            insertPostingEntry(runId, longValue(item.get("id")), "PARTNER_PAYABLE", "CREDIT", amount, currency, reference);
        }

        jdbcTemplate.update(
            "update corridor_settlement_batches set batch_status = 'POSTED', updated_at = current_timestamp where id = ?",
            corridorSettlementBatchId
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postingRunId", runId);
        result.put("settlementBatchId", corridorSettlementBatchId);
        result.put("settlementType", "CORRIDOR");
        result.put("postedTotal", postedTotal);
        result.put("expectedTotal", expectedTotal);
        result.put("varianceAmount", variance);
        result.put("entryCount", items.size() * 2);
        result.put("status", "POSTED");
        return result;
    }

    public List<Map<String, Object>> postingRuns(int limit) {
        return jdbcTemplate.queryForList(
            "select id, settlement_batch_id, settlement_batch_type, run_status, currency, expected_total, posted_total, "
                + "variance_amount, entry_count, created_at, posted_at from settlement_posting_runs order by created_at desc limit ?",
            limit
        );
    }

    private void insertPostingEntry(
        Long runId,
        Long itemId,
        String accountCode,
        String side,
        BigDecimal amount,
        String currency,
        String reference
    ) {
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
            "Automated settlement posting"
        );
    }

    private Map<String, Object> fetchOne(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, args);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Settlement record not found");
        }
        return rows.get(0);
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

    private static String value(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : value.toString();
    }
}
