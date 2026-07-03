package net.citotech.cito.balance;

import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Merchant;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class BalanceViewService {
    private final BalanceViewRepository repository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BalanceViewService(BalanceViewRepository repository, NamedParameterJdbcTemplate jdbcTemplate) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BalanceView> getBalances(Merchant merchant) {
        List<BalanceView> rows = repository.findByMerchant(merchant.getId());
        if (!rows.isEmpty()) {
            return rows;
        }
        return legacyFallback(merchant);
    }

    private List<BalanceView> legacyFallback(Merchant merchant) {
        List<BalanceView> results = new ArrayList<>();
        List<Balance> legacy = Common.getMerchantBalances(String.valueOf(merchant.getId()), jdbcTemplate);
        for (Balance item : legacy) {
            BalanceView row = new BalanceView();
            row.merchantId = merchant.getId();
            row.channelCode = item.getCode();
            row.gatewayId = item.getGateway_id();
            row.currency = item.getBaseCurrency() == null ? "" : item.getBaseCurrency();
            row.availableBalance = String.valueOf(item.getAmount());
            row.ledgerBalance = String.valueOf(item.getAmount());
            row.pendingBalance = "0.00";
            results.add(row);
        }
        return results;
    }
}
