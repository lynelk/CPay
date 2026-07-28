package net.citotech.cito.reporting;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only admin reporting endpoints backed by the nightly aggregates from audit F4 (transaction
 * stats), N10 (failure-reason analytics), and O3 (float dashboard). Secured the same as every
 * other {@code /api/v2/admin/**} route (see {@code SecurityConfig}: {@code hasRole("ADMIN")}).
 */
@RestController
@RequestMapping(path = "/api/v2/admin/reporting")
public class ReportingAdminController {

    private static final int DEFAULT_LOOKBACK_DAYS = 30;
    private static final int DEFAULT_BURN_RATE_WINDOW_DAYS = 7;
    private static final int DEFAULT_TOPUP_LOG_LIMIT = 50;

    private final ReportingQueryService queryService;

    public ReportingAdminController(ReportingQueryService queryService) {
        this.queryService = queryService;
    }

    /** Audit F4: pre-aggregated per-day transaction counts/amounts instead of scanning the log. */
    @GetMapping(path = "/transaction-stats")
    public List<Map<String, Object>> transactionStats(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "merchantId", required = false) Long merchantId,
            @RequestParam(value = "gatewayId", required = false) String gatewayId) {
        LocalDate toDate = parseDateOrDefault(to, LocalDate.now());
        LocalDate fromDate = parseDateOrDefault(from, toDate.minusDays(DEFAULT_LOOKBACK_DAYS));
        return queryService.transactionStats(fromDate, toDate, merchantId, gatewayId);
    }

    /** Audit N10: why transactions/payouts are failing, in aggregate, annotated via ErrorCatalog. */
    @GetMapping(path = "/failure-reasons")
    public List<Map<String, Object>> failureReasons(
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "gatewayId", required = false) String gatewayId) {
        LocalDate toDate = parseDateOrDefault(to, LocalDate.now());
        LocalDate fromDate = parseDateOrDefault(from, toDate.minusDays(DEFAULT_LOOKBACK_DAYS));
        return queryService.failureReasonStats(fromDate, toDate, gatewayId);
    }

    /**
     * Audit O3: per float/stock gateway account, a balance history, the computed burn rate
     * (average daily decrease over {@code windowDays}), a simple linear days-remaining forecast,
     * and the top-up log.
     */
    @GetMapping(path = "/float-dashboard")
    public Map<String, Object> floatDashboard(
            @RequestParam(value = "windowDays", defaultValue = "" + DEFAULT_BURN_RATE_WINDOW_DAYS) int windowDays) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (String accountType : queryService.distinctFloatAccountTypes()) {
            List<BalanceSnapshotPoint> snapshots = queryService.floatBalanceSnapshots(accountType, windowDays);
            BurnRateForecast forecast = FloatBurnRateCalculator.compute(snapshots, windowDays);

            Map<String, Object> account = new LinkedHashMap<>();
            account.put("accountType", accountType);
            account.put("snapshots", snapshots);
            account.put("currentBalance", forecast.currentBalance());
            account.put("burnRatePerDay", forecast.burnRatePerDay());
            account.put("estimatedDaysRemaining", forecast.estimatedDaysRemaining());
            accounts.add(account);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowDays", windowDays);
        result.put("accounts", accounts);
        result.put("topups", queryService.recentTopups(DEFAULT_TOPUP_LOG_LIMIT));
        return result;
    }

    /** Records a float/stock top-up event so the topup log has a way to grow (audit O3). */
    @PostMapping(path = "/float-dashboard/topups")
    public Map<String, Object> recordTopup(
            @RequestParam("account") String account,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam(value = "recordedBy", defaultValue = "admin") String recordedBy,
            @RequestParam(value = "note", required = false) String note) {
        int written = queryService.recordTopup(account, amount, recordedBy, note);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recorded", written > 0);
        return result;
    }

    private LocalDate parseDateOrDefault(String value, LocalDate defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return LocalDate.parse(value);
    }
}
