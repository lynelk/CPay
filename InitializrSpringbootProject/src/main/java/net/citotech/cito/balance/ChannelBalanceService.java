package net.citotech.cito.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Merchant;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChannelBalanceService {
    private final ChannelBalanceRepository channelBalanceRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ChannelBalanceService(ChannelBalanceRepository channelBalanceRepository,
                                 NamedParameterJdbcTemplate jdbcTemplate) {
        this.channelBalanceRepository = channelBalanceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ChannelBalance> getBalances(Merchant merchant) {
        List<ChannelBalance> normalized = channelBalanceRepository.findByMerchant(merchant.getId());
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return legacyFallback(merchant);
    }

    private List<ChannelBalance> legacyFallback(Merchant merchant) {
        List<ChannelBalance> results = new ArrayList<>();
        List<Balance> legacy = Common.getMerchantBalances(String.valueOf(merchant.getId()), jdbcTemplate);
        for (Balance balance : legacy) {
            ChannelBalance channelBalance = new ChannelBalance();
            channelBalance.setMerchantId(merchant.getId());
            channelBalance.setChannelCode(balance.getBalance_type());
            channelBalance.setGatewayId(balance.getGateway_id());
            channelBalance.setCurrency(balance.getCurrencyCode());
            channelBalance.setAvailableBalance(BigDecimal.valueOf(balance.getAmount()));
            channelBalance.setLedgerBalance(BigDecimal.valueOf(balance.getAmount()));
            channelBalance.setPendingBalance(BigDecimal.ZERO);
            results.add(channelBalance);
        }
        return results;
    }
}
