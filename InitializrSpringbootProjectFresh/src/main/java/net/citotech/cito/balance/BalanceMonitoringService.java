package net.citotech.cito.balance;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.citotech.cito.admin.FeatureFlagService;
import net.citotech.cito.admin.FeatureKeys;
import net.citotech.cito.crossborder.TreasuryPositionService;
import net.citotech.cito.crossborder.TreasuryPositionService.TreasuryPositionRow;
import net.citotech.cito.gateway.PaymentGatewayException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Balance-monitoring read surface (S5 pilot, gated by the {@code balance-monitoring} feature flag).
 * Combines the three balance views ops care about in one response:
 *
 * <ul>
 *   <li>current per-gateway float balances (float/stock merchant account via {@link
 *       FloatBalanceReader});
 *   <li>treasury positions (available/reserved/net per currency via TreasuryPositionService);
 *   <li>the most recent daily float snapshot per account type ({@code float_balance_snapshots},
 *       written nightly by ReportingAggregateService).
 * </ul>
 */
@Service
public class BalanceMonitoringService {

    private static final DateTimeFormatter SNAPSHOT_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final FloatBalanceReader floatBalanceReader;
    private final TreasuryPositionService treasuryPositionService;
    private final FeatureFlagService featureFlagService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public BalanceMonitoringService(
            FloatBalanceReader floatBalanceReader,
            TreasuryPositionService treasuryPositionService,
            FeatureFlagService featureFlagService,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.floatBalanceReader = floatBalanceReader;
        this.treasuryPositionService = treasuryPositionService;
        this.featureFlagService = featureFlagService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> overview() {
        if (!featureFlagService.isEnabled(FeatureKeys.BALANCE_MONITORING)) {
            throw new PaymentGatewayException(
                    "Balance monitoring is not enabled (feature flag balance-monitoring is off).");
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("generatedAt", Instant.now().toString());

        List<FloatBalanceReader.FloatBalanceRow> floats = floatBalanceReader.readCurrent();
        view.put("gatewayFloats", floats.stream().map(this::floatView).toList());

        List<TreasuryPositionRow> treasury = treasuryPositionService.listPositions();
        view.put("treasuryPositions", treasury.stream().map(this::treasuryView).toList());

        List<Map<String, Object>> snapshots = latestSnapshots();
        view.put("dailyFloatSnapshots", snapshots);
        return view;
    }

    private Map<String, Object> floatView(FloatBalanceReader.FloatBalanceRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("channelCode", row.channelCode());
        view.put("currency", row.currency());
        view.put("balance", row.balance());
        return view;
    }

    private Map<String, Object> treasuryView(TreasuryPositionRow row) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("currency", row.currency());
        view.put("availableBalance", row.availableBalance());
        view.put("reservedBalance", row.reservedBalance());
        view.put("netAvailable", row.netAvailable());
        view.put("status", row.status());
        view.put("updatedAt", row.updatedAt());
        return view;
    }

    private List<Map<String, Object>> latestSnapshots() {
        String sql =
                "SELECT s1.stat_date, s1.account_type, s1.balance "
                        + "FROM float_balance_snapshots s1 "
                        + "JOIN ("
                        + "  SELECT account_type, MAX(stat_date) AS max_date "
                        + "  FROM float_balance_snapshots GROUP BY account_type"
                        + ") s2 ON s2.account_type = s1.account_type AND s2.max_date = s1.stat_date "
                        + "ORDER BY s1.account_type";
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
        return rows.stream().map(this::snapshotView).toList();
    }

    private Map<String, Object> snapshotView(Map<String, Object> row) {
        Map<String, Object> view = new LinkedHashMap<>();
        Object statDate = row.get("stat_date");
        view.put("statDate", statDate == null ? null : String.valueOf(statDate));
        view.put("accountType", String.valueOf(row.get("account_type")));
        Object balance = row.get("balance");
        view.put("balance", balance instanceof Number number ? number : BigDecimal.ZERO);
        return view;
    }

    /** Formatting helper retained for callers that want a canonical snapshot date string. */
    public static String formatSnapshotDate(LocalDate date) {
        return date == null ? null : date.format(SNAPSHOT_DATE_FORMAT);
    }
}
