package net.citotech.cito.ledger;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DoubleEntryLedgerService {
    private static final int MONEY_SCALE = 4;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public DoubleEntryLedgerService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public long post(String transactionReference,
                     String sourceType,
                     String sourceReference,
                     String description,
                     List<LedgerEntryCommand> entries) {
        validateEntries(entries);
        Map<String, Totals> totals = totalsByCurrency(entries);
        for (Map.Entry<String, Totals> total : totals.entrySet()) {
            if (total.getValue().debits.compareTo(total.getValue().credits) != 0) {
                throw new PaymentGatewayException("Ledger transaction is not balanced for " + total.getKey());
            }
        }

        Long existing = findTransaction(transactionReference);
        if (existing != null) {
            return existing;
        }

        long txId = insertTransaction(transactionReference, sourceType, sourceReference, description);
        for (LedgerEntryCommand entry : entries) {
            long accountId = ensureAccount(entry);
            insertEntry(txId, accountId, entry);
        }
        return txId;
    }

    @Transactional
    public TrialBalanceResult runTrialBalance(LocalDate runDate, String currency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("run_date", runDate);
        p.addValue("currency", currency);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT "
                + "COALESCE(SUM(CASE WHEN le.entry_direction='DR' THEN le.amount ELSE 0 END), 0) AS debits, "
                + "COALESCE(SUM(CASE WHEN le.entry_direction='CR' THEN le.amount ELSE 0 END), 0) AS credits "
                + "FROM ledger_entries le "
                + "JOIN ledger_transactions lt ON lt.id = le.ledger_transaction_id "
                + "WHERE DATE(lt.created_at) <= :run_date AND le.currency=:currency",
            p);
        BigDecimal debits = decimal(row.get("debits"));
        BigDecimal credits = decimal(row.get("credits"));
        TrialBalanceResult result = new TrialBalanceResult(runDate, currency, debits, credits);

        MapSqlParameterSource write = new MapSqlParameterSource();
        write.addValue("run_date", runDate);
        write.addValue("currency", currency);
        write.addValue("debits", debits);
        write.addValue("credits", credits);
        write.addValue("balanced", result.isBalanced() ? "YES" : "NO");
        write.addValue("message", result.isBalanced() ? "balanced" : "debits and credits differ");
        jdbcTemplate.update(
            "INSERT INTO ledger_trial_balance_runs "
                + "(run_date, currency, total_debits, total_credits, balanced_flag, message) "
                + "VALUES (:run_date, :currency, :debits, :credits, :balanced, :message) "
                + "ON DUPLICATE KEY UPDATE total_debits=:debits, total_credits=:credits, "
                + "balanced_flag=:balanced, message=:message, created_at=CURRENT_TIMESTAMP",
            write);
        return result;
    }

    private void validateEntries(List<LedgerEntryCommand> entries) {
        if (entries == null || entries.size() < 2) {
            throw new PaymentGatewayException("Ledger transaction requires at least two entries");
        }
        for (LedgerEntryCommand entry : entries) {
            if (blank(entry.accountCode()) || blank(entry.direction()) || blank(entry.currency())) {
                throw new PaymentGatewayException("Ledger entry account, direction, and currency are required");
            }
            if (!"DR".equalsIgnoreCase(entry.direction()) && !"CR".equalsIgnoreCase(entry.direction())) {
                throw new PaymentGatewayException("Ledger entry direction must be DR or CR");
            }
            if (entry.amount() == null || entry.amount().compareTo(BigDecimal.ZERO) <= 0) {
                throw new PaymentGatewayException("Ledger entry amount must be greater than zero");
            }
        }
    }

    private Map<String, Totals> totalsByCurrency(List<LedgerEntryCommand> entries) {
        Map<String, Totals> totals = new LinkedHashMap<>();
        for (LedgerEntryCommand entry : entries) {
            String currency = entry.currency().trim().toUpperCase();
            Totals total = totals.computeIfAbsent(currency, ignored -> new Totals());
            BigDecimal amount = entry.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            if ("DR".equalsIgnoreCase(entry.direction())) {
                total.debits = total.debits.add(amount);
            } else {
                total.credits = total.credits.add(amount);
            }
        }
        return totals;
    }

    private Long findTransaction(String transactionReference) {
        List<Long> ids = jdbcTemplate.query(
            "SELECT id FROM ledger_transactions WHERE transaction_reference=:reference",
            new MapSqlParameterSource("reference", transactionReference),
            (rs, rowNum) -> rs.getLong("id"));
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long insertTransaction(String transactionReference, String sourceType, String sourceReference, String description) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("reference", transactionReference);
        p.addValue("source_type", sourceType);
        p.addValue("source_reference", sourceReference);
        p.addValue("description", description);
        try {
            jdbcTemplate.update(
                "INSERT INTO ledger_transactions "
                    + "(transaction_reference, source_type, source_reference, description) "
                    + "VALUES (:reference, :source_type, :source_reference, :description)",
                p);
        } catch (DuplicateKeyException ignored) {
            Long existing = findTransaction(transactionReference);
            if (existing != null) {
                return existing;
            }
            throw ignored;
        }
        Long id = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", new MapSqlParameterSource(), Long.class);
        return id == null ? 0L : id;
    }

    private long ensureAccount(LedgerEntryCommand entry) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("account_code", entry.accountCode());
        p.addValue("account_name", blank(entry.accountName()) ? entry.accountCode() : entry.accountName());
        p.addValue("account_type", blank(entry.accountType()) ? "CONTROL" : entry.accountType());
        p.addValue("owner_type", blank(entry.ownerType()) ? "SYSTEM" : entry.ownerType());
        p.addValue("owner_id", entry.ownerId());
        p.addValue("currency", entry.currency().trim().toUpperCase());
        jdbcTemplate.update(
            "INSERT INTO ledger_accounts "
                + "(account_code, account_name, account_type, owner_type, owner_id, currency) "
                + "VALUES (:account_code, :account_name, :account_type, :owner_type, :owner_id, :currency) "
                + "ON DUPLICATE KEY UPDATE account_name=:account_name, account_status='ACTIVE'",
            p);
        Long id = jdbcTemplate.queryForObject(
            "SELECT id FROM ledger_accounts WHERE account_code=:account_code",
            new MapSqlParameterSource("account_code", entry.accountCode()),
            Long.class);
        return id == null ? 0L : id;
    }

    private void insertEntry(long txId, long accountId, LedgerEntryCommand entry) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("ledger_transaction_id", txId);
        p.addValue("account_id", accountId);
        p.addValue("direction", entry.direction().trim().toUpperCase());
        p.addValue("amount", entry.amount().setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        p.addValue("currency", entry.currency().trim().toUpperCase());
        p.addValue("memo", entry.memo());
        jdbcTemplate.update(
            "INSERT INTO ledger_entries "
                + "(ledger_transaction_id, account_id, entry_direction, amount, currency, entry_memo) "
                + "VALUES (:ledger_transaction_id, :account_id, :direction, :amount, :currency, :memo)",
            p);
    }

    private BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return new BigDecimal(String.valueOf(value)).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class Totals {
        private BigDecimal debits = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        private BigDecimal credits = BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
