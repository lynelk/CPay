package net.citotech.cito.balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.citotech.cito.Common;
import net.citotech.cito.Model.Balance;
import net.citotech.cito.Model.Merchant;
import net.citotech.cito.Model.Setting;
import net.citotech.cito.gateway.PaymentChannelAdapter;
import net.citotech.cito.gateway.PaymentChannelRegistry;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the current gateway float balances off the float/stock merchant account - the same account
 * {@code FloatAlertScheduler} and {@code ReportingAggregateService} read - and maps each legacy
 * {@code merchant_statement} balance column to a normalized channel code + currency via the
 * payment-channel registry.
 *
 * <p>This is the read side for the S5 balance-monitoring pilot: without it, ops had to either wait
 * for the nightly {@code float_balance_snapshots} (ReportingAggregateService) or query the database
 * by hand to see today's float position per gateway.
 */
@Component
public class FloatBalanceReader {

    public static final String FLOAT_STOCK_ACCOUNT_SETTING = "float_stock_account";

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final PaymentChannelRegistry channelRegistry;

    public FloatBalanceReader(
            NamedParameterJdbcTemplate jdbcTemplate, PaymentChannelRegistry channelRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.channelRegistry = channelRegistry;
    }

    /** Current per-gateway float balances; empty when no float/stock account is configured. */
    public List<FloatBalanceRow> readCurrent() {
        Setting stockAccount = Common.getSettings(FLOAT_STOCK_ACCOUNT_SETTING, jdbcTemplate);
        if (stockAccount == null || isBlank(stockAccount.getSetting_value())) {
            return List.of();
        }
        Merchant floatMerchant =
                Common.getMerchantByAccountNumber(
                        stockAccount.getSetting_value().trim(), jdbcTemplate);
        if (floatMerchant == null) {
            return List.of();
        }
        List<Balance> legacy =
                Common.getMerchantBalances(String.valueOf(floatMerchant.getId()), jdbcTemplate);
        List<FloatBalanceRow> rows = new ArrayList<>();
        for (Balance balance : legacy) {
            String gatewayId = balance.getGateway_id();
            String channelCode =
                    channelRegistry
                            .findByLegacyGatewayId(gatewayId)
                            .map(PaymentChannelAdapter::channelCode)
                            .orElse(gatewayId == null ? "unknown" : gatewayId);
            String currency =
                    balance.getBaseCurrency() == null || balance.getBaseCurrency().isBlank()
                            ? inferCurrency(channelCode)
                            : balance.getBaseCurrency();
            rows.add(
                    new FloatBalanceRow(
                            channelCode, currency, BigDecimal.valueOf(balance.getAmount())));
        }
        return rows;
    }

    private String inferCurrency(String channelCode) {
        return channelCode != null && channelCode.toLowerCase().contains("safaricom")
                ? "KES"
                : "UGX";
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public record FloatBalanceRow(String channelCode, String currency, BigDecimal balance) {}
}
