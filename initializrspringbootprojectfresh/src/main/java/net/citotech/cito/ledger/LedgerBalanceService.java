package net.citotech.cito.ledger;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerBalanceService {
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public LedgerBalanceService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int refreshBalances() {
        return jdbcTemplate.update(
            "INSERT INTO ledger_account_balances "
                + "(account_id, account_code, currency, debit_total, credit_total, net_balance, last_ledger_entry_id, refreshed_at) "
                + "SELECT la.id, la.account_code, le.currency, "
                + "COALESCE(SUM(CASE WHEN le.entry_direction='DR' THEN le.amount ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN le.entry_direction='CR' THEN le.amount ELSE 0 END), 0), "
                + "COALESCE(SUM(CASE WHEN le.entry_direction='DR' THEN le.amount ELSE -le.amount END), 0), "
                + "MAX(le.id), CURRENT_TIMESTAMP "
                + "FROM ledger_accounts la "
                + "JOIN ledger_entries le ON le.account_id=la.id "
                + "GROUP BY la.id, la.account_code, le.currency "
                + "ON DUPLICATE KEY UPDATE "
                + "account_code=VALUES(account_code), debit_total=VALUES(debit_total), "
                + "credit_total=VALUES(credit_total), net_balance=VALUES(net_balance), "
                + "last_ledger_entry_id=VALUES(last_ledger_entry_id), refreshed_at=CURRENT_TIMESTAMP",
            new MapSqlParameterSource());
    }

    public List<Map<String, Object>> balancesForOwner(String ownerType, Long ownerId, String currency) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        p.addValue("owner_type", ownerType == null ? null : ownerType.trim().toUpperCase());
        p.addValue("owner_id", ownerId);
        p.addValue("currency", currency == null ? null : currency.trim().toUpperCase());
        return jdbcTemplate.queryForList(
            "SELECT la.account_code, la.account_name, la.account_type, la.owner_type, la.owner_id, "
                + "lab.currency, lab.debit_total, lab.credit_total, lab.net_balance, lab.refreshed_at "
                + "FROM ledger_account_balances lab "
                + "JOIN ledger_accounts la ON la.id=lab.account_id "
                + "WHERE (:owner_type IS NULL OR la.owner_type=:owner_type) "
                + "AND (:owner_id IS NULL OR la.owner_id=:owner_id) "
                + "AND (:currency IS NULL OR lab.currency=:currency) "
                + "ORDER BY la.owner_type, la.owner_id, lab.currency, la.account_code",
            p);
    }
}
